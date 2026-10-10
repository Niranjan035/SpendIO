package com.spendio.sharedkernel.money;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outcome of a single fx conversion (ARCHITECTURE.md D.5). {@code targetMinor} is the
 * already-rounded amount in the target currency (minor units); {@code rateId},
 * {@code rateDate} and {@code stale} let the caller attach provenance to a ledger row.
 */
public record ConversionResult(
        BigDecimal rate,
        long targetMinor,
        Long rateId,
        LocalDate rateDate,
        boolean stale) {
}