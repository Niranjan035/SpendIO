package com.spendio.imports.support;

import com.spendio.imports.matcher.DescriptionNormalizer;
import com.spendio.transactions.support.DedupeKeyFactory;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Deterministic content hash for duplicate detection within a file
 * (ARCHITECTURE.md D.12 step 5): date | absolute amount | currency | normalised
 * description. Independent from the ledger's content key, which additionally scopes
 * the account and user id.
 */
public final class DedupeHashFactory {

    private DedupeHashFactory() {
    }

    public static String dedupeHash(LocalDate occurredOn, Long amountMinor, String currencyCode,
            String description) {
        return DedupeKeyFactory.sha256Hex(String.join("|",
                String.valueOf(occurredOn),
                String.valueOf(Math.abs(amountMinor == null ? 0 : amountMinor)),
                currencyCode == null ? "" : currencyCode.toUpperCase(Locale.ROOT),
                DescriptionNormalizer.normalize(description)));
    }
}