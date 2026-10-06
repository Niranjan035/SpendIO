package com.spendio.sharedkernel.domain;

import java.util.Objects;

/**
 * An amount in minor units plus its ISO currency - the only money representation
 * allowed in the domain (never {@code double}, never {@code BigDecimal} for storage).
 */
public record Money(long amountMinor, CurrencyCode currency) {

    public Money {
        Objects.requireNonNull(currency, "currency must not be null");
    }

    public static Money of(long amountMinor, String currencyCode) {
        return new Money(amountMinor, CurrencyCode.of(currencyCode));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public int signum() {
        return Long.signum(amountMinor);
    }

    public Money abs() {
        return amountMinor < 0 ? negate() : this;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "cannot combine different currencies: " + currency + " and " + other.currency);
        }
    }
}
