package com.spendio.transactions.service;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.LedgerChangedEvent;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.CreateTransferRequest;
import com.spendio.transactions.dto.response.TransferResponse;
import com.spendio.transactions.mapper.TransactionMapper;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.spec.TransactionSpecifications;
import com.spendio.transactions.support.DedupeKeyFactory;
import com.spendio.transactions.support.LedgerFx;
import com.spendio.transactions.support.TransactionSortSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfers (ARCHITECTURE.md D.8, E.8): two linked legs sharing a
 * {@code transfer_group_id}, each with its own currency, frozen FX triple and
 * counterparty link. Same-currency transfers move identical amounts; cross-currency
 * transfers convert the destination amount once (or take the caller's explicit
 * {@code toAmountMinor}). Reversal lives in {@link TransactionReversalService} so the
 * pair always flips together.
 */
@Service
public class TransferService {

    private final TransactionRepository transactions;
    private final AccountQueryService accountQuery;
    private final ObjectProvider<FxConversionPort> fxPort;
    private final BaseCurrencyPort baseCurrency;
    private final EntityManager entityManager;
    private final TransactionMapper mapper;
    private final ApplicationEventPublisher events;
    private final int maxFutureDays;

    public TransferService(TransactionRepository transactions, AccountQueryService accountQuery,
            ObjectProvider<FxConversionPort> fxPort, BaseCurrencyPort baseCurrency, EntityManager entityManager,
            TransactionMapper mapper, ApplicationEventPublisher events,
            @Value("${spendio.transactions.max-future-days:365}") int maxFutureDays) {
        this.transactions = transactions;
        this.accountQuery = accountQuery;
        this.fxPort = fxPort;
        this.baseCurrency = baseCurrency;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.events = events;
        this.maxFutureDays = maxFutureDays;
    }

    @Transactional
    public TransferResponse create(Long userId, CreateTransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("toAccountId", "must differ from fromAccountId");
        }
        AccountResponse from = accountQuery.detail(userId, request.fromAccountId());
        AccountResponse to = accountQuery.detail(userId, request.toAccountId());
        requireActive(from);
        requireActive(to);

        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(from.currencyCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("currencyCode", "must match the source account currency " + from.currencyCode());
        }
        if (request.occurredOn().isAfter(LocalDate.now().plusDays(maxFutureDays))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("occurredOn", "cannot be more than " + maxFutureDays + " days in the future");
        }

        String toCurrency = to.currencyCode();
        long toAmount = destinationAmount(request, currency, toCurrency);

        String base = baseCurrency.baseCurrencyCode(userId);
        FxConversionPort fx = fxPort.getIfAvailable();
        LedgerFx.Freeze fromFreeze = LedgerFx.freeze(base, fx, request.amountMinor(), currency, request.occurredOn());
        LedgerFx.Freeze toFreeze = LedgerFx.freeze(base, fx, toAmount, toCurrency, request.occurredOn());

        String transferGroupId = UUID.randomUUID().toString();
        String description = trimToNull(request.description());
        String notes = trimToNull(request.notes());

        Transaction fromLeg = Transaction.create(
                userId,
                entityManager.getReference(Account.class, from.id()),
                null,
                TransactionType.TRANSFER,
                TransactionDirection.OUTFLOW,
                request.amountMinor(),
                currency,
                fromFreeze.exchangeRate(),
                fromFreeze.baseAmountMinor(),
                fromFreeze.fxRateId(),
                request.occurredOn(),
                TransactionStatus.POSTED,
                description,
                null,
                null,
                notes,
                dedupeKeyFor(userId, from.id(), request.occurredOn(), request.amountMinor(), currency, description));
        Transaction toLeg = Transaction.create(
                userId,
                entityManager.getReference(Account.class, to.id()),
                null,
                TransactionType.TRANSFER,
                TransactionDirection.INFLOW,
                toAmount,
                toCurrency,
                toFreeze.exchangeRate(),
                toFreeze.baseAmountMinor(),
                toFreeze.fxRateId(),
                request.occurredOn(),
                TransactionStatus.POSTED,
                description,
                null,
                null,
                notes,
                dedupeKeyFor(userId, to.id(), request.occurredOn(), toAmount, toCurrency, description));
        fromLeg.linkTransfer(transferGroupId, null);
        toLeg.linkTransfer(transferGroupId, null);
        transactions.save(fromLeg);
        transactions.save(toLeg);
        fromLeg.linkTransfer(transferGroupId, toLeg.getId());
        toLeg.linkTransfer(transferGroupId, fromLeg.getId());
        events.publishEvent(new LedgerChangedEvent(userId));
        return mapper.toTransferResponse(transferGroupId, List.of(fromLeg, toLeg), base);
    }

    /**
     * Recurring TRANSFER generation (ARCHITECTURE.md D.9/J.5): identical to
     * {@link #create} except the occurrence date comes from the schedule (no future
     * cap) and both legs link back to the rule and run that produced them, so run
     * history survives rule deletion. A run-now {@code idempotencyKey} rides on the
     * OUTFLOW leg's dedupe key (replays resolve through that key); the INFLOW leg
     * keeps the content key.
     */
    @Transactional
    public TransferResponse createForRecurring(Long userId, CreateTransferRequest request,
            Long recurringTransactionId, Long recurringRunId, String idempotencyKey) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("toAccountId", "must differ from fromAccountId");
        }
        AccountResponse from = accountQuery.detail(userId, request.fromAccountId());
        AccountResponse to = accountQuery.detail(userId, request.toAccountId());
        requireActive(from);
        requireActive(to);

        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(from.currencyCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("currencyCode", "must match the source account currency " + from.currencyCode());
        }

        String toCurrency = to.currencyCode();
        long toAmount = destinationAmount(request, currency, toCurrency);

        String base = baseCurrency.baseCurrencyCode(userId);
        FxConversionPort fx = fxPort.getIfAvailable();
        LedgerFx.Freeze fromFreeze = LedgerFx.freeze(base, fx, request.amountMinor(), currency, request.occurredOn());
        LedgerFx.Freeze toFreeze = LedgerFx.freeze(base, fx, toAmount, toCurrency, request.occurredOn());

        String transferGroupId = UUID.randomUUID().toString();
        String description = trimToNull(request.description());
        String notes = trimToNull(request.notes());

        Transaction fromLeg = Transaction.create(
                userId,
                entityManager.getReference(Account.class, from.id()),
                null,
                TransactionType.TRANSFER,
                TransactionDirection.OUTFLOW,
                request.amountMinor(),
                currency,
                fromFreeze.exchangeRate(),
                fromFreeze.baseAmountMinor(),
                fromFreeze.fxRateId(),
                request.occurredOn(),
                TransactionStatus.POSTED,
                description,
                null,
                null,
                notes,
                recurringDedupeKey(userId, idempotencyKey, from.id(), request.occurredOn(), request.amountMinor(),
                        currency, description));
        Transaction toLeg = Transaction.create(
                userId,
                entityManager.getReference(Account.class, to.id()),
                null,
                TransactionType.TRANSFER,
                TransactionDirection.INFLOW,
                toAmount,
                toCurrency,
                toFreeze.exchangeRate(),
                toFreeze.baseAmountMinor(),
                toFreeze.fxRateId(),
                request.occurredOn(),
                TransactionStatus.POSTED,
                description,
                null,
                null,
                notes,
                dedupeKeyFor(userId, to.id(), request.occurredOn(), toAmount, toCurrency, description));
        fromLeg.linkTransfer(transferGroupId, null);
        toLeg.linkTransfer(transferGroupId, null);
        transactions.save(fromLeg);
        transactions.save(toLeg);
        fromLeg.linkTransfer(transferGroupId, toLeg.getId());
        toLeg.linkTransfer(transferGroupId, fromLeg.getId());
        fromLeg.linkRecurring(recurringTransactionId, recurringRunId);
        toLeg.linkRecurring(recurringTransactionId, recurringRunId);
        events.publishEvent(new LedgerChangedEvent(userId));
        return mapper.toTransferResponse(transferGroupId, List.of(fromLeg, toLeg), base);
    }

    /** Transfer groups newest-first; {@code accountId} matches either leg. */
    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> list(Long userId, LocalDate from, LocalDate to, Long accountId,
            Integer page, Integer size) {
        String base = baseCurrency.baseCurrencyCode(userId);
        Page<Transaction> fromLegs = transactions.findAll(
                TransactionSpecifications.withReferences().and(fromLegSpec(userId, from, to, accountId)),
                TransactionSortSupport.pageable(page, size, null));
        List<TransferResponse> content = pairUp(userId, fromLegs.getContent(), base);
        return PageResponse.of(content, fromLegs.getNumber(), fromLegs.getSize(), fromLegs.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TransferResponse detail(Long userId, String transferGroupId) {
        String base = baseCurrency.baseCurrencyCode(userId);
        List<TransferResponse> pairs = pairUp(userId, List.of(), base, transferGroupId);
        if (pairs.isEmpty()) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transfer not found")
                    .withProperty("transferGroupId", transferGroupId);
        }
        return pairs.get(0);
    }

    /** Loads the original legs of the given groups and pairs them per group. */
    private List<TransferResponse> pairUp(Long userId, List<Transaction> fromLegs, String base, String... groupIds) {
        Set<String> wanted = new LinkedHashSet<>(List.of(groupIds));
        fromLegs.forEach(leg -> wanted.add(leg.getTransferGroupId()));
        if (wanted.isEmpty()) {
            return List.of();
        }
        Map<String, List<Transaction>> byGroup = transactions
                .findByUserIdAndTransferGroupIdInAndDeletedAtIsNullAndReversalOfTransactionIdIsNull(userId, wanted)
                .stream()
                .sorted(Comparator.comparing(Transaction::getId))
                .collect(Collectors.groupingBy(Transaction::getTransferGroupId, LinkedHashMap::new,
                        Collectors.toList()));
        List<TransferResponse> pairs = new ArrayList<>();
        if (groupIds.length > 0) {
            for (String groupId : groupIds) {
                List<Transaction> legs = byGroup.get(groupId);
                if (legs != null && legs.size() == 2) {
                    pairs.add(mapper.toTransferResponse(groupId, legs, base));
                }
            }
            return pairs;
        }
        for (Transaction fromLeg : fromLegs) {
            List<Transaction> legs = byGroup.get(fromLeg.getTransferGroupId());
            if (legs != null && legs.size() == 2) {
                pairs.add(mapper.toTransferResponse(fromLeg.getTransferGroupId(), legs, base));
            }
        }
        return pairs;
    }

    /**
     * One OUTFLOW leg per group is the paging unit (every group has exactly one), so
     * pages never split a pair; {@code accountId} matches either side of the transfer.
     */
    private Specification<Transaction> fromLegSpec(Long userId, LocalDate from, LocalDate to, Long accountId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.equal(root.get("type"), TransactionType.TRANSFER));
            predicates.add(cb.equal(root.get("direction"), TransactionDirection.OUTFLOW));
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.isNull(root.get("reversalOfTransactionId")));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredOn"), to));
            }
            if (accountId != null) {
                jakarta.persistence.criteria.Subquery<Long> other = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<Transaction> leg = other.from(Transaction.class);
                other.select(leg.get("id"));
                other.where(
                        cb.equal(leg.get("id"), root.get("counterpartyTransactionId")),
                        cb.equal(leg.get("account").get("id"), accountId),
                        cb.isNull(leg.get("deletedAt")));
                predicates.add(cb.or(
                        cb.equal(root.get("account").get("id"), accountId),
                        cb.exists(other)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private long destinationAmount(CreateTransferRequest request, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            if (request.toAmountMinor() != null && request.toAmountMinor() != request.amountMinor()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("toAmountMinor",
                                "must equal amountMinor for a same-currency transfer");
            }
            return request.amountMinor();
        }
        if (request.toAmountMinor() != null) {
            return request.toAmountMinor();
        }
        return LedgerFx.convert(fxPort.getIfAvailable(), request.amountMinor(), fromCurrency, toCurrency,
                request.occurredOn());
    }

    private String dedupeKeyFor(Long userId, Long accountId, LocalDate occurredOn, long amountMinor,
            String currency, String description) {
        String candidate = DedupeKeyFactory.contentKey(userId, accountId, occurredOn, amountMinor, currency,
                description);
        return transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(userId, candidate) ? null : candidate;
    }

    /** Run-now token wins over the content key on the source leg (D.9 replay path). */
    private String recurringDedupeKey(Long userId, String idempotencyKey, Long accountId, LocalDate occurredOn,
            long amountMinor, String currency, String description) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return DedupeKeyFactory.idempotencyKey(userId, idempotencyKey);
        }
        return dedupeKeyFor(userId, accountId, occurredOn, amountMinor, currency, description);
    }

    private static void requireActive(AccountResponse account) {
        if (account.status() == AccountStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED,
                    "Transfers cannot touch an archived account")
                    .withProperty("accountId", account.id());
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
