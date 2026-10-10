package com.spendio.imports.matcher;

import java.util.Locale;

/** Canonical description form for dedupe and fuzzy matching (ARCHITECTURE.md D.12). */
public final class DescriptionNormalizer {

    private DescriptionNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}