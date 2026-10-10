package com.spendio.transactions.service;

import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.domain.Account;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.LedgerChangedEvent;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.ConversionResult;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.dto.request.BulkCategoryRequest;
import com.spendio.transactions.dto.request.BulkDeleteRequest;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.dto.request.UpdateTransactionRequest;
import com.spendio.transactions.dto.response.BulkUpdateResponse;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.mapper.TransactionMapper;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.support.DedupeKeyFactory;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger writes (ARCHITECTURE.md D.8): create with FX freeze and dedupe, PATCH with
 * optimistic locking, soft delete, and the two bulk actions. Cross-module reads go
 * through {@link AccountQueryService} / {@link CategoryQueryService} - never their
 * repositories (B.3).
 */
@Service
public class TransactionService {

    /** Marker namespace reserved for opening-balance rows the balance maths skips. */
    private static final String OPENING_DEDUPE_PREFIX = "opening:";

    private final TransactionRepository transactions;
    private final AccountQueryService accountQuery;
    private final CategoryQueryService categoryQuery;
    private final ObjectProvider<FxConversionPort> fxPort;
    private final BaseCurrencyPort baseCurrency;
    private final EntityManager entityManager;
    private final TransactionMapper mapper;
    private final ApplicationEventPublisher events;
    private final int maxFutureDays;

    public TransactionService(TransactionRepository transactions, AccountQueryService accountQuery,
            CategoryQueryService categoryQuery, ObjectProvider<FxConversionPort> fxPort,
            BaseCurrencyPort baseCurrency, EntityManager entityManager, TransactionMapper mapper,
            ApplicationEventPublisher events,
            @Value("${spendio.transactions.max-future-days:365}") int maxFutureDays) {
        this.transactions = transactions;
        this.accountQuery = accountQuery;
        this.categoryQuery = categoryQuery;
        this.fxPort = fxPort;
        this.baseCurrency = baseCurrency;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.events = events;
        this.maxFutureDays = maxFutureDays;
    }

    @Transactional
    public TransactionResponse create(Long userId, CreateTransactionRequest request, String idempotencyKey) {
        String base = baseCurrency.baseCurrencyCode(userId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String token = DedupeKeyFactory.idempotencyKey(userId, idempotencyKey);
            Optional<Transaction> replay = transactions
                    .findFirstByUserIdAndDedupeKeyAndDeletedAtIsNullOrderByIdDesc(userId, token);
            if (replay.isPresent()) {
                return mapper.toResponse(replay.get(), base);
            }
        }

        AccountResponse account = accountQuery.detail(userId, request.accountId());
        if (account.status() == AccountStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED,
                    "Transactions cannot be added to an archived account")
                    .withProperty("accountId", request.accountId());
        }
        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(account.currencyCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("currencyCode", "must match the account currency " + account.currencyCode());
        }

        TransactionType type = request.type();
        if (type == TransactionType.TRANSFER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("type", "transfers are created through the transfer endpoint");
        }
        TransactionDirection direction = resolveDirection(type, request.direction());

        Category category = null;
        if (type == TransactionType.INCOME || type == TransactionType.EXPENSE) {
            if (request.categoryId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("categoryId", "is required for income and expense transactions");
            }
            CategoryResponse categoryDto = categoryQuery.detail(userId, request.categoryId());
            if (!categoryMatches(type, categoryDto.type())) {
                throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH,
                        "Category does not match the transaction type")
                        .withProperty("categoryId", request.categoryId())
                        .withProperty("type", type.name());
            }
            category = entityManager.getReference(Category.class, categoryDto.id());
        } else {
            if (request.categoryId() != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("categoryId", "must be empty for adjustment transactions");
            }
            if (request.direction() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("direction", "is required for adjustment transactions");
            }
        }

        TransactionStatus status = request.status() == null ? TransactionStatus.POSTED : request.status();
        if (status == TransactionStatus.VOID && (type == TransactionType.INCOME || type == TransactionType.EXPENSE)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("status", "cannot be VOID for income or expense transactions");
        }
        if (request.occurredOn().isAfter(LocalDate.now().plusDays(maxFutureDays))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("occurredOn", "cannot be more than " + maxFutureDays + " days in the future");
        }

        Freeze freeze = freeze(userId, request.amountMinor(), currency, request.exchangeRate(), request.occurredOn());
        String dedupeKey = dedupeKeyFor(userId, request, idempotencyKey, account);

        Transaction tx = Transaction.create(
                userId,
                entityManager.getReference(Account.class, account.id()),
                category,
                type,
                direction,
                request.amountMinor(),
                currency,
                freeze.exchangeRate(),
                freeze.baseAmountMinor(),
                freeze.fxRateId(),
                request.occurredOn(),
                status,
                trimToNull(request.description()),
                trimToNull(request.merchant()),
                trimToNull(request.payee()),
                trimToNull(request.notes()),
                dedupeKey);
        transactions.save(tx);
        ledgerChanged(userId);
        return mapper.toResponse(tx, base);
    }

    /**
     * Recurring generator entry point (ARCHITECTURE.md D.9 step 3): same validation and
     * freeze as {@link #create}, but the date comes from the schedule (the future cap
     * does not apply), the row links back to its rule and run, and the run-level unique
     * key - not a replay probe - is what prevents double posting. Joins the caller's
     * transaction so rule, run row and ledger row commit together.
     */
    @Transactional
    public TransactionResponse createForRecurring(Long userId, CreateTransactionRequest request,
            Long recurringTransactionId, Long recurringRunId, String idempotencyKey) {
        String base = baseCurrency.baseCurrencyCode(userId);
        AccountResponse account = accountQuery.detail(userId, request.accountId());
        if (account.status() == AccountStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED,
                    "Transactions cannot be added to an archived account")
                    .withProperty("accountId", request.accountId());
        }
        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(account.currencyCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("currencyCode", "must match the account currency " + account.currencyCode());
        }
        TransactionType type = request.type();
        if (type == TransactionType.TRANSFER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("type", "transfers are created through the transfer endpoint");
        }
        TransactionDirection direction = type == TransactionType.INCOME
                ? TransactionDirection.INFLOW
                : TransactionDirection.OUTFLOW;

        Category category = null;
        if (request.categoryId() != null) {
            CategoryResponse categoryDto = categoryQuery.detail(userId, request.categoryId());
            if (!categoryMatches(type, categoryDto.type())) {
                throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH,
                        "Category does not match the transaction type")
                        .withProperty("categoryId", request.categoryId())
                        .withProperty("type", type.name());
            }
            category = entityManager.getReference(Category.class, categoryDto.id());
        }

        Freeze freeze = freeze(userId, request.amountMinor(), currency, null, request.occurredOn());
        String dedupeKey = dedupeKeyFor(userId, request, idempotencyKey, account);

        Transaction tx = Transaction.create(
                userId,
                entityManager.getReference(Account.class, account.id()),
                category,
                type,
                direction,
                request.amountMinor(),
                currency,
                freeze.exchangeRate(),
                freeze.baseAmountMinor(),
                freeze.fxRateId(),
                request.occurredOn(),
                request.status() == null ? TransactionStatus.POSTED : request.status(),
                trimToNull(request.description()),
                null,
                null,
                trimToNull(request.notes()),
                dedupeKey);
        tx.linkRecurring(recurringTransactionId, recurringRunId);
        transactions.save(tx);
        ledgerChanged(userId);
        return mapper.toResponse(tx, base);
    }

    /** The run that produced the ledger row carrying this dedupe key (run-now replay). */
    @Transactional(readOnly = true)
    public Optional<Long> recurringRunForDedupeKey(Long userId, String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return Optional.empty();
        }
        return transactions.findFirstByUserIdAndDedupeKeyAndDeletedAtIsNullOrderByIdDesc(userId, dedupeKey)
                .map(Transaction::getRecurringRunId)
                .filter(java.util.Objects::nonNull);
    }

    /**
     * CSV import entry point (ARCHITECTURE.md D.12 step 8): same validation and freeze
     * as {@link #createForRecurring}, but the row links back to its import job and source
     * row so the import can be undone or reported later. Joins the caller's transaction
     * so the import row and ledger row commit together.
     */
    @Transactional
    public TransactionResponse createForCsvImport(Long userId, CreateTransactionRequest request,
            Long csvImportId, Long csvImportRowId, String idempotencyKey) {
        String base = baseCurrency.baseCurrencyCode(userId);
        AccountResponse account = accountQuery.detail(userId, request.accountId());
        if (account.status() == AccountStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED,
                    "Transactions cannot be added to an archived account")
                    .withProperty("accountId", request.accountId());
        }
        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currency.equals(account.currencyCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("currencyCode", "must match the account currency " + account.currencyCode());
        }
        TransactionType type = request.type();
        if (type == TransactionType.TRANSFER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("type", "transfers are created through the transfer endpoint");
        }
        TransactionDirection direction = type == TransactionType.INCOME
                ? TransactionDirection.INFLOW
                : TransactionDirection.OUTFLOW;

        Category category = null;
        if (request.categoryId() != null) {
            CategoryResponse categoryDto = categoryQuery.detail(userId, request.categoryId());
            if (!categoryMatches(type, categoryDto.type())) {
                throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH,
                        "Category does not match the transaction type")
                        .withProperty("categoryId", request.categoryId())
                        .withProperty("type", type.name());
            }
            category = entityManager.getReference(Category.class, categoryDto.id());
        }

        Freeze freeze = freeze(userId, request.amountMinor(), currency, null, request.occurredOn());
        String dedupeKey = dedupeKeyFor(userId, request, idempotencyKey, account);

        Transaction tx = Transaction.create(
                userId,
                entityManager.getReference(Account.class, account.id()),
                category,
                type,
                direction,
                request.amountMinor(),
                currency,
                freeze.exchangeRate(),
                freeze.baseAmountMinor(),
                freeze.fxRateId(),
                request.occurredOn(),
                request.status() == null ? TransactionStatus.POSTED : request.status(),
                trimToNull(request.description()),
                null,
                null,
                trimToNull(request.notes()),
                dedupeKey);
        tx.linkCsvImport(csvImportId, csvImportRowId);
        transactions.save(tx);
        ledgerChanged(userId);
        return mapper.toResponse(tx, base);
    }

    /**
     * CSV import duplicate probe (ARCHITECTURE.md D.12): the id of the newest
     * non-deleted ledger row carrying {@code dedupeKey}, if any. Exposed for the
     * imports module so it never touches the transactions repository (B.3) - the
     * returned id is what a duplicate mark points at.
     */
    @Transactional(readOnly = true)
    public Optional<Long> existingTransactionIdForDedupeKey(Long userId, String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return Optional.empty();
        }
        return transactions.findFirstByUserIdAndDedupeKeyAndDeletedAtIsNullOrderByIdDesc(userId, dedupeKey)
                .map(Transaction::getId);
    }

    /**
     * Import undo (ARCHITECTURE.md D.12 E.12): soft-deletes the ledger rows an import
     * created, scoped to the caller's user, and flushes so the delete is visible to
     * the caller's REQUIRES_NEW transaction. Lives here so the imports module never
     * reaches into the transactions repository (B.3).
     */
    @Transactional
    public void undoImportedTransactions(Long userId, List<Long> txIds) {
        if (txIds == null || txIds.isEmpty()) {
            return;
        }
        for (Transaction tx : transactions.findAllById(txIds)) {
            if (tx.getUserId().equals(userId) && !tx.isDeleted()) {
                tx.softDelete();
            }
        }
        transactions.flush();
        ledgerChanged(userId);
    }

    /**
     * Rule DELETE (ARCHITECTURE.md J.8): keeps the ledger rows and the run log, drops
     * only the rule pointer so history survives with {@code recurring_run_id} intact.
     */
    @Transactional
    public int unlinkRecurring(Long userId, Long recurringTransactionId) {
        return transactions.clearRecurringLinks(userId, recurringTransactionId);
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, UpdateTransactionRequest request) {
        Transaction tx = requireOwned(userId, id);
        if (tx.getType() == TransactionType.TRANSFER) {
            throw immutable("Transfer legs are edited through the transfer endpoint");
        }
        if (tx.getReversalOfTransactionId() != null) {
            throw immutable("A reversal entry cannot be edited");
        }
        if (tx.getReversedAt() != null) {
            throw immutable("A reversed transaction cannot be edited");
        }
        if (request.version() != null && !request.version().equals(tx.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "The transaction was modified by another request")
                    .withProperty("expectedVersion", request.version())
                    .withProperty("currentVersion", tx.getVersion());
        }

        boolean currencyChanged = request.currencyCode() != null
                && !request.currencyCode().equalsIgnoreCase(tx.getCurrencyCode());
        String effectiveCurrency = currencyChanged
                ? request.currencyCode().trim().toUpperCase(Locale.ROOT)
                : tx.getCurrencyCode();
        boolean accountChanged = request.accountId() != null
                && !request.accountId().equals(tx.getAccount().getId());

        AccountResponse account = null;
        if (accountChanged || currencyChanged) {
            Long accountId = accountChanged ? request.accountId() : tx.getAccount().getId();
            account = accountQuery.detail(userId, accountId);
            if (account.status() == AccountStatus.ARCHIVED) {
                throw new BusinessException(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED,
                        "Transactions cannot be moved to an archived account")
                        .withProperty("accountId", accountId);
            }
            if (!account.currencyCode().equals(effectiveCurrency)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("accountId", "must hold currency " + effectiveCurrency);
            }
        }

        if (request.categoryId() != null && !request.categoryId().equals(categoryIdOf(tx))) {
            if (tx.getType() == TransactionType.ADJUSTMENT) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("categoryId", "cannot be set on an adjustment transaction");
            }
            CategoryResponse categoryDto = categoryQuery.detail(userId, request.categoryId());
            if (!categoryMatches(tx.getType(), categoryDto.type())) {
                throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH,
                        "Category does not match the transaction type")
                        .withProperty("categoryId", request.categoryId())
                        .withProperty("type", tx.getType().name());
            }
            tx.setCategory(entityManager.getReference(Category.class, categoryDto.id()));
        }

        if (request.occurredOn() != null) {
            tx.setOccurredOn(request.occurredOn());
        }
        boolean amountChanged = request.amountMinor() != null && request.amountMinor() != tx.getAmountMinor();
        if (amountChanged || currencyChanged) {
            long amount = amountChanged ? request.amountMinor() : tx.getAmountMinor();
            Freeze freeze = freeze(userId, amount, effectiveCurrency, null, tx.getOccurredOn());
            tx.applyFrozenAmount(amount, freeze.exchangeRate(), freeze.baseAmountMinor(), freeze.fxRateId());
        }
        if (accountChanged) {
            tx.setAccount(entityManager.getReference(Account.class, account.id()));
        }
        if (request.description() != null) {
            tx.setDescription(trimToNull(request.description()));
        }
        if (request.notes() != null) {
            tx.setNotes(trimToNull(request.notes()));
        }
        ledgerChanged(userId);
        return mapper.toResponse(tx, baseCurrency.baseCurrencyCode(userId));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transaction tx = requireOwned(userId, id);
        if (tx.getType() == TransactionType.TRANSFER) {
            throw immutable("Transfer legs are deleted through the transfer endpoint");
        }
        if (tx.getReversalOfTransactionId() != null) {
            throw immutable("A reversal entry cannot be deleted");
        }
        if (tx.getReversedAt() != null) {
            throw immutable("A reversed transaction cannot be deleted");
        }
        tx.softDelete();
        ledgerChanged(userId);
    }

    @Transactional
    public BulkUpdateResponse recategorise(Long userId, BulkCategoryRequest request) {
        CategoryResponse category = categoryQuery.detail(userId, request.categoryId());
        List<Transaction> rows = loadOwnedStrict(userId, request.ids());
        int updated = 0;
        for (Transaction tx : rows) {
            if (!eligibleForBulkEdit(tx)) {
                continue;
            }
            if (!categoryMatches(tx.getType(), category.type())) {
                throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH,
                        "Category does not match the transaction type")
                        .withProperty("categoryId", request.categoryId())
                        .withProperty("type", tx.getType().name());
            }
            tx.setCategory(entityManager.getReference(Category.class, category.id()));
            updated++;
        }
        ledgerChanged(userId);
        return new BulkUpdateResponse(updated);
    }

    @Transactional
    public BulkUpdateResponse bulkDelete(Long userId, BulkDeleteRequest request) {
        List<Transaction> rows = loadOwnedStrict(userId, request.ids());
        int updated = 0;
        for (Transaction tx : rows) {
            if (tx.getType() == TransactionType.TRANSFER || tx.getReversalOfTransactionId() != null
                    || tx.getReversedAt() != null) {
                continue;
            }
            tx.softDelete();
            updated++;
        }
        ledgerChanged(userId);
        return new BulkUpdateResponse(updated);
    }

    /** Notifies the derived views (analytics cache, balances) that the ledger moved. */
    private void ledgerChanged(Long userId) {
        events.publishEvent(new LedgerChangedEvent(userId));
    }

    private String dedupeKeyFor(Long userId, CreateTransactionRequest request, String idempotencyKey,
            AccountResponse account) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return DedupeKeyFactory.idempotencyKey(userId, idempotencyKey);
        }
        if (request.dedupeKey() != null && !request.dedupeKey().isBlank()) {
            String bodyKey = request.dedupeKey().trim();
            if (bodyKey.toLowerCase(Locale.ROOT).startsWith(OPENING_DEDUPE_PREFIX)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("dedupeKey", "starts with a reserved prefix");
            }
            if (transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(userId, bodyKey)) {
                throw new BusinessException(ErrorCode.TRANSACTION_DUPLICATE, "Duplicate transaction")
                        .withProperty("dedupeKey", bodyKey);
            }
            return bodyKey;
        }
        String candidate = DedupeKeyFactory.contentKey(userId, account.id(), request.occurredOn(),
                request.amountMinor(), request.currencyCode(), request.description());
        return transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(userId, candidate) ? null : candidate;
    }

    private TransactionDirection resolveDirection(TransactionType type, TransactionDirection requested) {
        if (type == TransactionType.INCOME) {
            if (requested != null && requested != TransactionDirection.INFLOW) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("direction", "must be INFLOW for income transactions");
            }
            return TransactionDirection.INFLOW;
        }
        if (type == TransactionType.EXPENSE) {
            if (requested != null && requested != TransactionDirection.OUTFLOW) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                        .withFieldError("direction", "must be OUTFLOW for expense transactions");
            }
            return TransactionDirection.OUTFLOW;
        }
        return requested;
    }

    private Freeze freeze(Long userId, long amountMinor, String currency, BigDecimal overrideRate,
            LocalDate occurredOn) {
        String base = baseCurrency.baseCurrencyCode(userId);
        if (currency.equals(base)) {
            return new Freeze(overrideRate == null ? BigDecimal.ONE : overrideRate, amountMinor, null);
        }
        FxConversionPort port = fxPort.getIfAvailable();
        if (port == null) {
            throw new BusinessException(ErrorCode.FX_RATE_UNAVAILABLE, "FX rate unavailable")
                    .withProperty("currencyCode", currency);
        }
        if (overrideRate != null) {
            BigDecimal txToBase = invert(overrideRate);
            ConversionResult result = port.convertWithRate(amountMinor, currency, base, txToBase);
            return new Freeze(overrideRate, result.targetMinor(), null);
        }
        ConversionResult result = port.convert(amountMinor, currency, base, occurredOn);
        return new Freeze(invert(result.rate()), result.targetMinor(), result.rateId());
    }

    /** The column stores base->transaction; the port speaks transaction->base. */
    private static BigDecimal invert(BigDecimal rate) {
        if (rate.signum() == 0) {
            throw new BusinessException(ErrorCode.FX_RATE_UNAVAILABLE, "FX rate unavailable");
        }
        return BigDecimal.ONE.divide(rate, 10, RoundingMode.HALF_EVEN);
    }

    private List<Transaction> loadOwnedStrict(Long userId, List<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        List<Transaction> rows = transactions.findAllById(distinct);
        if (rows.size() != distinct.size()) {
            throw notFound(null);
        }
        for (Transaction tx : rows) {
            if (!Objects.equals(userId, tx.getUserId()) || tx.getDeletedAt() != null) {
                throw notFound(tx.getId());
            }
        }
        List<Transaction> ordered = new ArrayList<>(rows);
        return ordered;
    }

    private boolean eligibleForBulkEdit(Transaction tx) {
        return tx.getType() != TransactionType.TRANSFER
                && tx.getType() != TransactionType.ADJUSTMENT
                && tx.getStatus() != TransactionStatus.VOID
                && tx.getReversalOfTransactionId() == null
                && tx.getReversedAt() == null;
    }

    private Transaction requireOwned(Long userId, Long id) {
        return transactions.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound(id));
    }

    private static BusinessException notFound(Long id) {
        BusinessException ex = new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found");
        return id == null ? ex : ex.withProperty("id", id);
    }

    private static BusinessException immutable(String message) {
        return new BusinessException(ErrorCode.TRANSACTION_IMMUTABLE, message);
    }

    private static boolean categoryMatches(TransactionType type, Category.Type categoryType) {
        return (type == TransactionType.INCOME) == (categoryType == Category.Type.INCOME);
    }

    private static Long categoryIdOf(Transaction tx) {
        return tx.getCategory() == null ? null : tx.getCategory().getId();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Freeze(BigDecimal exchangeRate, long baseAmountMinor, Long fxRateId) {
    }
}
