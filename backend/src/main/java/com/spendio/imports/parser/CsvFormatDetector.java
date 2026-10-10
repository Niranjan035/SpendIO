package com.spendio.imports.parser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Detects the delimiter and encoding of a CSV sample (ARCHITECTURE.md D.12 step 2).
 * The delimiter is decided by counting occurrences outside quoted sections on the
 * first physical line; encoding by BOM, falling back to UTF-8.
 */
public final class CsvFormatDetector {

    private static final char[] CANDIDATES = {',', ';', '\t', '|'};

    private CsvFormatDetector() {
    }

    public record CsvFormat(char delimiter, String encoding) {
    }

    public static CsvFormat detect(byte[] sample, String delimiterOverride, String encodingOverride) {
        String encoding = encodingOverride != null && !encodingOverride.isBlank()
                ? encodingOverride
                : detectEncoding(sample);
        String content = new String(sample, Charset.forName(encoding));
        char delimiter = delimiterOverride != null && !delimiterOverride.isBlank()
                ? delimiterOverride.charAt(0)
                : detectDelimiter(content);
        return new CsvFormat(delimiter, encoding);
    }

    private static String detectEncoding(byte[] sample) {
        if (startsWith(sample, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return "UTF-8";
        }
        if (startsWith(sample, (byte) 0xFF, (byte) 0xFE)) {
            return "UTF-16LE";
        }
        if (startsWith(sample, (byte) 0xFE, (byte) 0xFF)) {
            return "UTF-16BE";
        }
        return StandardCharsets.UTF_8.name();
    }

    private static char detectDelimiter(String content) {
        String firstLine = firstPhysicalLine(content);
        char best = ',';
        int bestCount = -1;
        for (char candidate : CANDIDATES) {
            int count = countOutsideQuotes(firstLine, candidate);
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return bestCount > 0 ? best : ',';
    }

    static String firstPhysicalLine(String content) {
        int end = content.indexOf('\n');
        String first = end < 0 ? content : content.substring(0, end);
        return first.endsWith("\r") ? first.substring(0, first.length() - 1) : first;
    }

    static int countOutsideQuotes(String line, char needle) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == needle && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    private static boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}