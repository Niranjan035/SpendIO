package com.spendio.imports.matcher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lenient date parsing (ARCHITECTURE.md D.12 step 4): a user-supplied format hint is
 * tried first, then a candidate list of common bank/CSV layouts plus epoch timestamps.
 * Ambiguous day-first dates resolve as day-first by default, matching EU bank exports.
 */
public final class DateParser {

    private static final List<DateTimeFormatter> CANDIDATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("MM.dd.yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("MM/dd/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH))
                    .optionalStart().appendLiteral(' ').append(DateTimeFormatter.ofPattern("HH:mm:ss")).optionalEnd()
                    .toFormatter(Locale.ENGLISH));

    private DateParser() {
    }

    public static Optional<LocalDate> tryParse(String raw, String formatHint) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1).trim();
        }

        if (formatHint != null && !formatHint.isBlank()) {
            Optional<LocalDate> byHint = parseAs(text, formatHint);
            if (byHint.isPresent()) {
                return byHint;
            }
        }

        if (text.matches("\\d{10,13}")) {
            return parseEpoch(text);
        }

        if (text.indexOf(',') >= 0) {
            try {
                return Optional.of(
                        ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate());
            } catch (DateTimeParseException notRfc1123) {
                // fall through to the plain candidates
            }
        }

        for (DateTimeFormatter formatter : CANDIDATES) {
            Optional<LocalDate> parsed = tryFormatter(text, formatter);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> parseAs(String text, String format) {
        try {
            LocalDate date = LocalDate.parse(text, DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
            return Optional.of(date);
        } catch (DateTimeParseException cannotParse) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> tryFormatter(String text, DateTimeFormatter formatter) {
        try {
            return Optional.of(LocalDate.parse(text, formatter));
        } catch (DateTimeParseException notThisFormat) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> parseEpoch(String text) {
        try {
            long value = Long.parseLong(text);
            long millis = value < 100_000_000_000L ? value * 1000L : value;
            return Optional.of(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate());
        } catch (RuntimeException notEpoch) {
            return Optional.empty();
        }
    }
}