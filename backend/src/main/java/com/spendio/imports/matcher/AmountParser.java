package com.spendio.imports.matcher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Converts a CSV money cell into minor units (ARCHITECTURE.md D.12 step 4). Handles
 * paren negatives, CR/DR suffixes, currency symbols and codes, and both comma and dot
 * decimal conventions. The sign is preserved so debit/credit semantics survive.
 */
public final class AmountParser {

    private AmountParser() {
    }

    public static OptionalLong parseMinor(String raw) {
        return parseMinor(raw, 2);
    }

    public static OptionalLong parseMinor(String raw, int fractionDigits) {
        if (raw == null) {
            return OptionalLong.empty();
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return OptionalLong.empty();
        }
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).trim();
        }

        boolean negative = false;
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true;
            text = text.substring(1, text.length() - 1).trim();
        } else if (text.startsWith("-")) {
            negative = true;
            text = text.substring(1).trim();
        } else if (text.startsWith("+")) {
            text = text.substring(1).trim();
        }

        text = text.replace("$", "").replace("€", "").replace("£", "").replace("¥", "")
                .replace("₹", "").replace("\u20bd", "").trim();

        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" DR")) {
            negative = !negative;
            text = text.substring(0, text.length() - 3).trim();
        } else if (upper.startsWith("DR ")) {
            negative = !negative;
            text = text.substring(3).trim();
        } else if (upper.endsWith(" CR")) {
            text = text.substring(0, text.length() - 3).trim();
        } else if (upper.startsWith("CR ")) {
            text = text.substring(3).trim();
        }

        text = text.replaceAll("[A-Za-z]{2,3}\\s*$", "").trim();
        if (text.isEmpty()) {
            return OptionalLong.empty();
        }

        String compact = text.replace(" ", "").replace("\u00a0", "");
        if (compact.isEmpty()) {
            return OptionalLong.empty();
        }

        String normalized = normalizeSeparators(compact);
        BigDecimal value;
        try {
            value = new BigDecimal(normalized);
        } catch (NumberFormatException notANumber) {
            return OptionalLong.empty();
        }
        bigDecimalCheck(value);
        BigDecimal minor = value.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP);
        bigDecimalCheck(minor);
        long result = negative ? -minor.longValueExact() : minor.longValueExact();
        return OptionalLong.of(result);
    }

    /**
     * Resolves grouping vs decimal separators: when both are present the last one wins
     * (1.234,56 -> dot grouping, comma decimal). A lone comma/dot followed by at most
     * two digits is the decimal mark, otherwise it is thousands grouping.
     */
    private static String normalizeSeparators(String compact) {
        boolean hasDot = compact.indexOf('.') >= 0;
        boolean hasComma = compact.indexOf(',') >= 0;
        char grouping = 0;
        char decimal = 0;

        if (hasDot && hasComma) {
            int dot = compact.lastIndexOf('.');
            int comma = compact.lastIndexOf(',');
            if (dot > comma) {
                decimal = '.';
                grouping = ',';
            } else {
                decimal = ',';
                grouping = '.';
            }
        } else if (hasComma) {
            int comma = compact.lastIndexOf(',');
            if (compact.length() - comma - 1 <= 2 && compact.length() - comma - 1 > 0) {
                decimal = ',';
            } else {
                grouping = ',';
            }
        } else if (hasDot) {
            int dot = compact.lastIndexOf('.');
            if (compact.length() - dot - 1 <= 2 && compact.length() - dot - 1 > 0) {
                decimal = '.';
            } else {
                grouping = '.';
            }
        }

        String normalized = compact.replace(String.valueOf(grouping), "");
        if (decimal != 0) {
            normalized = normalized.replace(decimal, '.');
        }
        return normalized;
    }

    private static void bigDecimalCheck(BigDecimal value) {
        if (value.precision() - value.scale() > 18) {
            throw new IllegalArgumentException("amount out of range: " + value);
        }
    }
}