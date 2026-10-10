package com.spendio.transactions.service;

import com.spendio.accounts.domain.Account;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.ledger.AdjustmentPort;
import com.spendio.sharedkernel.events.LedgerChangedEvent;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.support.LedgerFx;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Balance reconciliation writes (ARCHITECTURE.md D.8): an ADJUSTMENT row with no
 * category, the stated reason in {@code notes}, and the FX freeze resolved from the
 * row's own currency and date. Reached from the accounts module through
 * {@link AdjustmentPort} - B.3 forbids an accounts -> transactions dependency.
 */
@Service
public class TransactionAdjustmentService implements AdjustmentPort {

    private final TransactionRepository transactions;
    private final BaseCurrencyPort baseCurrency;
    private final ObjectProvider<FxConversionPort> fxPort;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public TransactionAdjustmentService(TransactionRepository transactions, BaseCurrencyPort baseCurrency,
            ObjectProvider<FxConversionPort> fxPort, EntityManager entityManager,
            ApplicationEventPublisher events) {
        this.transactions = transactions;
        this.baseCurrency = baseCurrency;
        this.fxPort = fxPort;
        this.entityManager = entityManager;
        this.events = events;
    }

    @Override
    @Transactional
    public void adjust(Long userId, Long accountId, TransactionDirection direction, long amountMinor,
            String currencyCode, LocalDate occurredOn, String reason) {
        LedgerFx.Freeze freeze = LedgerFx.freeze(
                baseCurrency.baseCurrencyCode(userId), fxPort.getIfAvailable(), amountMinor, currencyCode, occurredOn);
        Transaction tx = Transaction.create(
                userId,
                entityManager.getReference(Account.class, accountId),
                null,
                TransactionType.ADJUSTMENT,
                direction,
                amountMinor,
                currencyCode,
                freeze.exchangeRate(),
                freeze.baseAmountMinor(),
                freeze.fxRateId(),
                occurredOn,
                TransactionStatus.POSTED,
                null,
                null,
                null,
                trimToNull(reason),
                null);
        transactions.save(tx);
        events.publishEvent(new LedgerChangedEvent(userId));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
