package com.spendio.fx.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Conversion outcome (ARCHITECTURE.md D.5). {@code source} names the resolution path
 * that produced the rate (manual, provider, inverse(...), triangulated, fetch) and
 * {@code stale} marks the fallback to an older known rate.
 */
public record ConvertResponse(
        long fromAmountMinor,
        long toAmountMinor,
        BigDecimal rate,
        LocalDate rateDate,
        String source,
        boolean stale) {
}