package com.spendio.sharedkernel.ledger;

import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import java.time.LocalDate;

/**
 * Write path for balance-reconciling ADJUSTMENT rows (ARCHITECTURE.md D.8, E.3).
 *
 * <p>B.3 forbids an accounts -> transactions dependency, so the accounts module reaches
 * the ledger's adjustment writer through this shared-kernel port - the same inversion
 * {@link com.spendio.sharedkernel.money.FxConversionPort} uses. The transactions module
 * implements it; ownership, archived-account and delta validation stay with the caller.
 */
public interface AdjustmentPort {

    /**
     * Inserts a POSTED, category-less ADJUSTMENT row of {@code amountMinor} in
     * {@code currencyCode} on {@code occurredOn}; {@code reason} is stored in notes.
     * The base amount and frozen rate are resolved from the row's own currency and date.
     */
    void adjust(Long userId, Long accountId, TransactionDirection direction, long amountMinor,
            String currencyCode, LocalDate occurredOn, String reason);
}
