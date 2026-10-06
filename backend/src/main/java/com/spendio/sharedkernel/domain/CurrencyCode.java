package com.spendio.sharedkernel.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ISO-4217 alphabetic code validated at the boundary (ARCHITECTURE.md C.2).
 */
public record CurrencyCode(String value) {

    private static final Pattern ISO_4217 = Pattern.compile("[A-Z]{3}");

    public CurrencyCode {
        Objects.requireNonNull(value, "currency code must not be null");
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        if (!ISO_4217.matcher(normalised).matches()) {
            throw new IllegalArgumentException("not a valid ISO-4217 currency code: " + value);
        }
        value = normalised;
    }

    public static CurrencyCode of(String value) {
        return new CurrencyCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
