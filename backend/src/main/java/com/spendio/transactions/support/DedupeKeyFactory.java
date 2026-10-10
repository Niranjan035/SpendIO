package com.spendio.transactions.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Deterministic SHA-256 keys behind dedupe and idempotent create
 * (ARCHITECTURE.md D.8): a header token namespaces the user, the content key
 * normalises description case/whitespace so "  Coffee  " equals "coffee".
 */
public final class DedupeKeyFactory {

    private DedupeKeyFactory() {
    }

    /** 64-char hex token for the {@code Idempotency-Key} header, scoped to the user. */
    public static String idempotencyKey(Long userId, String headerValue) {
        return sha256Hex(userId + "|" + headerValue.trim());
    }

    /** 64-char hex content hash used when the request carries no explicit dedupe key. */
    public static String contentKey(Long userId, Long accountId, LocalDate occurredOn, long amountMinor,
            String currencyCode, String description) {
        return sha256Hex(String.join("|",
                String.valueOf(userId),
                String.valueOf(accountId),
                String.valueOf(occurredOn),
                String.valueOf(amountMinor),
                currencyCode.toUpperCase(Locale.ROOT),
                normaliseDescription(description)));
    }

    /** Lower-cased, trimmed, whitespace-collapsed description used for keying and probes. */
    public static String normaliseDescription(String description) {
        if (description == null) {
            return "";
        }
        return description.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String sha256Hex(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the JDK", impossible);
        }
    }
}
