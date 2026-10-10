package com.spendio.reference.dto;

import com.spendio.reference.domain.Currency;

/** Currency as the API exposes it (ARCHITECTURE.md D.4). */
public record CurrencyResponse(String code, String name, int minorUnit, String symbol, boolean isActive) {

    public static CurrencyResponse from(Currency currency) {
        return new CurrencyResponse(
                currency.getCode(),
                currency.getName(),
                currency.getMinorUnit(),
                currency.getSymbol(),
                currency.isActive());
    }
}
