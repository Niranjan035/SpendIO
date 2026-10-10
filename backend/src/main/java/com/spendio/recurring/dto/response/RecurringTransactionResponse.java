package com.spendio.recurring.dto.response;

import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Recurring rule read (ARCHITECTURE.md D.9). {@code notes}, {@code maxRetries},
 * {@code failureReason}, {@code targetOccurrenceDate} and {@code toAccount} are additive
 * beyond the spec list so the edit form round-trips and the failure banner can render;
 * {@code exchangeRate} is intentionally not echoed (display-only frozen rate).
 */
public record RecurringTransactionResponse(
        Long id,
        AccountRef account,
        AccountRef toAccount,
        CategoryRef category,
        TransactionType type,
        TransactionDirection direction,
        long amountMinor,
        String currencyCode,
        long baseAmountMinor,
        String description,
        String notes,
        RecurrenceFrequency frequency,
        short intervalCount,
        Integer dayOfMonth,
        Integer dayOfWeek,
        boolean skipWeekends,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate targetOccurrenceDate,
        LocalDate nextRunDate,
        LocalDate lastRunDate,
        RecurringStatus status,
        boolean autoPost,
        short maxRetries,
        short retryCount,
        String lastError,
        String failureReason,
        int generatedCount,
        Instant createdAt) {

    public record AccountRef(Long id, String name, String currencyCode) {
    }

    public record CategoryRef(Long id, String name, Category.Type type, String color, String icon) {
    }
}
