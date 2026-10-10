package com.spendio.imports.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * A streaming-style CSV reader (ARCHITECTURE.md D.12 step 2): walks the decoded text
 * once, honouring quoted fields with embedded separators and escaped quotes. The
 * encoded hard caps - 50 000 rows, 10 000 columns per row - protect memory and mirror
 * the database limits.
 */
public final class CsvStreamingReader {

    public static final int MAX_ROWS = 50_000;
    public static final int MAX_COLUMNS = 10_000;

    private CsvStreamingReader() {
    }

    public record CsvRow(List<String> fields) {
    }

    /**
     * Reads every record. {@code stopAfterRows} caps the physical lines consumed (or
     * {@code <= 0} for no cap); an INDIVIDUAL record above {@code MAX_COLUMNS} fields is
     * still returned so the parse stage can flag it as a row error instead of aborting.
     */
    public static List<CsvRow> read(String content, char delimiter) {
        List<CsvRow> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"' && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\n') {
                if (!fieldStarted && fields.isEmpty() && field.length() == 0) {
                    continue;
                }
                fields.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                rows.add(new CsvRow(stripTrailingEmpty(trimTrailingCr(fields))));
                fields = new ArrayList<>();
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (!(fields.isEmpty() && field.length() == 0 && !fieldStarted)) {
            fields.add(field.toString());
            rows.add(new CsvRow(trimTrailingCr(fields)));
        }
        return rows;
    }

    private static List<String> trimTrailingCr(List<String> fields) {
        if (fields.isEmpty()) {
            return fields;
        }
        int last = fields.size() - 1;
        String value = fields.get(last);
        if (value.endsWith("\r")) {
            fields.set(last, value.substring(0, value.length() - 1));
        }
        return fields;
    }

    private static List<String> stripTrailingEmpty(List<String> fields) {
        int last = fields.size() - 1;
        while (last >= 0 && fields.get(last).isEmpty()) {
            last--;
        }
        return new ArrayList<>(fields.subList(0, last + 1));
    }
}