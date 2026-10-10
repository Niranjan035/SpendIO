package com.spendio.imports.parser;

import java.util.ArrayList;
import java.util.List;

/** Header detection and cleaning (ARCHITECTURE.md D.12 step 2). */
public final class CsvHeaderAnalyzer {

    private CsvHeaderAnalyzer() {
    }

    /**
     * A data row almost always has a numeric field (date, amount, or quantity). If no
     * field parses as numeric, treat the record as a header row.
     */
    public static boolean looksLikeHeader(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        for (String field : fields) {
            if (isNumeric(field)) {
                return false;
            }
        }
        return fields.stream().anyMatch(CsvHeaderAnalyzer::hasLetter);
    }

    /** Blank columns become {@code column_N} so raw lookups never lose a value. */
    public static List<String> cleanHeaders(List<String> fields) {
        List<String> headers = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            String candidate = fields.get(i);
            String header = candidate == null ? "" : candidate.trim().replaceAll("\\s+", " ");
            headers.add(header.isEmpty() ? "column_" + (i + 1) : header);
        }
        return headers;
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String compact = value.trim().replaceAll("[$€£¥\\s(),]", "");
        if (compact.isEmpty()) {
            return false;
        }
        compact = compact.replaceAll("\\.(?=[0-9]{3})([^,]*$)?", "");
        try {
            Double.parseDouble(compact);
            return true;
        } catch (NumberFormatException notNumeric) {
            return false;
        }
    }

    private static boolean hasLetter(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}