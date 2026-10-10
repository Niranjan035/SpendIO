package com.spendio.imports.support;

/**
 * CSV formula injection guard (ARCHITECTURE.md D.12): values beginning with =, +, - or
 * @ are quoted with a leading apostrophe so a spreadsheet never executes them.
 */
public final class CsvFormulaSanitizer {

    private static final String[] DANGEROUS = {"=", "+", "-", "@"};

    private CsvFormulaSanitizer() {
    }

    public static String sanitize(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return text;
        }
        for (String prefix : DANGEROUS) {
            if (text.startsWith(prefix)) {
                return "'" + text;
            }
        }
        return text;
    }
}