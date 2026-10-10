package com.spendio.fx.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A rate as the API exposes it (ARCHITECTURE.md D.5): who it came from and when. */
public record FxRateResponse(
        String baseCurrencyCode,
        String quoteCurrencyCode,
        BigDecimal rate,
        LocalDate rateDate,
        String source) {
}