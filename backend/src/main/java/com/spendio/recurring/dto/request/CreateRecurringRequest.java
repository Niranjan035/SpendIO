package com.spendio.recurring.dto.request;

import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Create a recurring rule (ARCHITECTURE.md D.9, E.9). {@code direction} is derived from
 * {@code type}; TRANSFER rules carry {@code toAccountId} and no category (J.5). The FX
 * triple is always frozen from the daily series as of each occurrence (J.6), so unlike
 * one-off transactions there is no caller-supplied rate override.
 */
public record CreateRecurringRequest(
        @NotNull Long accountId,
        Long toAccountId,
        Long categoryId,
        @NotNull TransactionType type,
        @NotNull @Positive Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        @Size(max = 255) String description,
        @Size(max = 16383) String notes,
        @NotNull RecurrenceFrequency frequency,
        @NotNull @Min(1) Integer intervalCount,
        @Min(1) @Max(31) Integer dayOfMonth,
        @Min(1) @Max(7) Integer dayOfWeek,
        Boolean skipWeekends,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean autoPost,
        @Min(1) @Max(99) Integer maxRetries,
        LocalDate targetOccurrenceDate) {
}
