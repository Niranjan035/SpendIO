package com.spendio.recurring.dto.request;

import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Partial edit of a recurring rule (ARCHITECTURE.md E.9 PATCH). {@code null} fields are
 * left untouched, blank strings clear description/notes - the merged rule is re-validated
 * as a whole, so a frequency change together with stale day fields is rejected.
 * {@code version} is the optimistic-lock check when supplied.
 */
public record UpdateRecurringRequest(
        Long accountId,
        Long toAccountId,
        Long categoryId,
        TransactionType type,
        @Positive Long amountMinor,
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        @Size(max = 255) String description,
        @Size(max = 16383) String notes,
        RecurrenceFrequency frequency,
        @Min(1) Integer intervalCount,
        @Min(1) @Max(31) Integer dayOfMonth,
        @Min(1) @Max(7) Integer dayOfWeek,
        Boolean skipWeekends,
        LocalDate startDate,
        LocalDate endDate,
        Boolean autoPost,
        @Min(1) @Max(99) Integer maxRetries,
        LocalDate targetOccurrenceDate,
        Integer version) {
}
