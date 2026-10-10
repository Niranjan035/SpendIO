package com.spendio.transactions.service;

import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.LedgerChangedEvent;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.ReverseTransactionRequest;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.dto.response.TransferResponse;
import com.spendio.transactions.mapper.TransactionMapper;
import com.spendio.transactions.repository.TransactionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reversals (ARCHITECTURE.md D.8, E.7/E.8): a mirrored entry with direction flipped,
 * the same absolute amounts and the same frozen FX triple, so the pair nets to zero in
 * every currency. The original is marked with {@code reversed_at} and the reason lands
 * in {@code reversal_reason} on both rows. Reversal is single-shot: a row that is
 * already reversed, is itself a reversal, or is VOID cannot be reversed; transfer legs
 * only reverse through the whole group so the pair stays balanced.
 */
@Service
public class TransactionReversalService {

    private final TransactionRepository transactions;
    private final BaseCurrencyPort baseCurrency;
    private final TransactionMapper mapper;
    private final ApplicationEventPublisher events;
    private final int maxFutureDays;

    public TransactionReversalService(TransactionRepository transactions, BaseCurrencyPort baseCurrency,
            TransactionMapper mapper, ApplicationEventPublisher events,
            @Value("${spendio.transactions.max-future-days:365}") int maxFutureDays) {
        this.transactions = transactions;
        this.baseCurrency = baseCurrency;
        this.mapper = mapper;
        this.events = events;
        this.maxFutureDays = maxFutureDays;
    }

    /** Single-row reversal; returns the created mirrored entry (the original is marked). */
    @Transactional
    public TransactionResponse reverse(Long userId, Long id, ReverseTransactionRequest request) {
        Transaction original = requireOwned(userId, id);
        if (original.getType() == TransactionType.TRANSFER) {
            throw new BusinessException(ErrorCode.TRANSACTION_IMMUTABLE,
                    "Transfer legs are reversed through the transfer endpoint")
                    .withProperty("id", id);
        }
        requireReversible(original, request);
        Transaction mirror = createMirror(original, request, null, null);
        original.markReversed(Instant.now(), trimToNull(request.reason()));
        events.publishEvent(new LedgerChangedEvent(userId));
        return mapper.toResponse(mirror, baseCurrency.baseCurrencyCode(userId));
    }

    /**
     * Atomic group reversal (ARCHITECTURE.md D.8): both legs are validated before
     * either is touched, then mirrored into the same transfer group with the mirrored
     * legs cross-linked as counterparts.
     */
    @Transactional
    public TransferResponse reverseTransfer(Long userId, String transferGroupId, ReverseTransactionRequest request) {
        List<Transaction> legs = transactions
                .findByUserIdAndTransferGroupIdAndDeletedAtIsNull(userId, transferGroupId).stream()
                .filter(tx -> tx.getReversalOfTransactionId() == null)
                .sorted(Comparator.comparing(Transaction::getId))
                .toList();
        if (legs.size() != 2) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transfer not found")
                    .withProperty("transferGroupId", transferGroupId);
        }
        Transaction fromLeg = legs.stream()
                .filter(tx -> tx.getDirection() == TransactionDirection.OUTFLOW).findFirst().orElse(null);
        Transaction toLeg = legs.stream()
                .filter(tx -> tx.getDirection() == TransactionDirection.INFLOW).findFirst().orElse(null);
        if (fromLeg == null || toLeg == null
                || fromLeg.getType() != TransactionType.TRANSFER || toLeg.getType() != TransactionType.TRANSFER) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transfer not found")
                    .withProperty("transferGroupId", transferGroupId);
        }

        requireReversible(fromLeg, request);
        requireReversible(toLeg, request);

        Transaction mirrorFrom = createMirror(fromLeg, request, transferGroupId, toLeg.getId());
        Transaction mirrorTo = createMirror(toLeg, request, transferGroupId, fromLeg.getId());
        mirrorFrom.linkTransfer(transferGroupId, mirrorTo.getId());
        mirrorTo.linkTransfer(transferGroupId, mirrorFrom.getId());

        Instant now = Instant.now();
        String reason = trimToNull(request.reason());
        fromLeg.markReversed(now, reason);
        toLeg.markReversed(now, reason);
        events.publishEvent(new LedgerChangedEvent(userId));
        return mapper.toTransferResponse(transferGroupId, List.of(fromLeg, toLeg),
                baseCurrency.baseCurrencyCode(userId));
    }

    private Transaction createMirror(Transaction original, ReverseTransactionRequest request,
            String transferGroupId, Long counterpartyTransactionId) {
        Transaction mirror = Transaction.create(
                original.getUserId(),
                original.getAccount(),
                original.getCategory(),
                mirrorTypeOf(original.getType()),
                flip(original.getDirection()),
                original.getAmountMinor(),
                original.getCurrencyCode(),
                original.getExchangeRate(),
                original.getBaseAmountMinor(),
                original.getFxRateId(),
                request.occurredOn(),
                TransactionStatus.POSTED,
                original.getDescription(),
                original.getMerchant(),
                original.getPayee(),
                original.getNotes(),
                null);
        mirror.markAsReversalOf(original.getId(), trimToNull(request.reason()));
        if (transferGroupId != null) {
            mirror.linkTransfer(transferGroupId, counterpartyTransactionId);
        }
        return transactions.save(mirror);
    }

    /**
     * INCOME and EXPENSE must swap type because the DB couples type to direction
     * (chk_tx_type_direction); TRANSFER and ADJUSTMENT carry any direction, so they
     * keep their type and only flip the sign.
     */
    private static TransactionType mirrorTypeOf(TransactionType type) {
        if (type == TransactionType.INCOME) {
            return TransactionType.EXPENSE;
        }
        if (type == TransactionType.EXPENSE) {
            return TransactionType.INCOME;
        }
        return type;
    }

    private void requireReversible(Transaction original, ReverseTransactionRequest request) {
        if (original.getReversalOfTransactionId() != null) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_REVERSIBLE,
                    "A reversal entry cannot be reversed again")
                    .withProperty("id", original.getId());
        }
        if (original.getReversedAt() != null) {
            throw new BusinessException(ErrorCode.TRANSACTION_ALREADY_REVERSED,
                    "Transaction already reversed")
                    .withProperty("id", original.getId());
        }
        if (original.getStatus() == TransactionStatus.VOID) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_REVERSIBLE,
                    "A void transaction cannot be reversed")
                    .withProperty("id", original.getId());
        }
        if (request.occurredOn().isAfter(LocalDate.now().plusDays(maxFutureDays))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("occurredOn", "cannot be more than " + maxFutureDays + " days in the future");
        }
    }

    private Transaction requireOwned(Long userId, Long id) {
        return transactions.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found")
                        .withProperty("id", id));
    }

    private static TransactionDirection flip(TransactionDirection direction) {
        return direction == TransactionDirection.INFLOW
                ? TransactionDirection.OUTFLOW
                : TransactionDirection.INFLOW;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
