package com.spendio.transactions.support;

import com.spendio.transactions.dto.response.TransactionResponse;
import java.util.List;

/**
 * RFC-4180-ish CSV for {@code GET /transactions/export} (ARCHITECTURE.md E.6):
 * fixed column order, CRLF row breaks, quotes only where needed.
 */
public final class TransactionCsvWriter {

    private static final String[] HEADER = {
            "id", "occurredOn", "type", "direction", "amountMinor", "currencyCode",
            "baseAmountMinor", "account", "category", "description", "merchant", "payee",
            "notes", "status"
    };

    private TransactionCsvWriter() {
    }

    public static String write(List<TransactionResponse> rows) {
        StringBuilder out = new StringBuilder();
        row(out, List.of(HEADER));
        for (TransactionResponse tx : rows) {
            row(out, List.of(
                    String.valueOf(tx.id()),
                    String.valueOf(tx.occurredOn()),
                    tx.type().name(),
                    tx.direction().name(),
                    String.valueOf(tx.amountMinor()),
                    tx.currencyCode(),
                    String.valueOf(tx.baseAmountMinor()),
                    tx.account() == null ? "" : tx.account().name(),
                    tx.category() == null ? "" : tx.category().name(),
                    nullSafe(tx.description()),
                    nullSafe(tx.merchant()),
                    nullSafe(tx.payee()),
                    nullSafe(tx.notes()),
                    tx.status().name()));
        }
        return out.toString();
    }

    private static void row(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(cells.get(i)));
        }
        out.append("\r\n");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
