package com.spendio.recurring.mapper;

import com.spendio.recurring.domain.RecurringRun;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.recurring.dto.response.OccurrencePreviewResponse;
import com.spendio.recurring.dto.response.RecurringRunResponse;
import com.spendio.recurring.dto.response.RecurringTransactionResponse;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Rule and run to their read DTOs: nested account/category refs come from the (lazily
 * loaded) associations, so call inside an open transaction - list reads fetch-join them.
 */
@Component
public class RecurringMapper {

    public RecurringTransactionResponse toResponse(RecurringTransaction rule) {
        RecurringTransactionResponse.AccountRef account = new RecurringTransactionResponse.AccountRef(
                rule.getAccount().getId(), rule.getAccount().getName(), rule.getAccount().getCurrencyCode());
        RecurringTransactionResponse.AccountRef toAccount = rule.getToAccount() == null ? null
                : new RecurringTransactionResponse.AccountRef(
                        rule.getToAccount().getId(), rule.getToAccount().getName(),
                        rule.getToAccount().getCurrencyCode());
        RecurringTransactionResponse.CategoryRef category = rule.getCategory() == null ? null
                : new RecurringTransactionResponse.CategoryRef(
                        rule.getCategory().getId(), rule.getCategory().getName(), rule.getCategory().getType(),
                        rule.getCategory().getColor(), rule.getCategory().getIcon());
        return new RecurringTransactionResponse(
                rule.getId(),
                account,
                toAccount,
                category,
                rule.getType(),
                rule.getDirection(),
                rule.getAmountMinor(),
                rule.getCurrencyCode(),
                rule.getBaseAmountMinor(),
                rule.getDescription(),
                rule.getNotes(),
                rule.getFrequency(),
                rule.getIntervalCount(),
                rule.getDayOfMonth(),
                rule.getDayOfWeek(),
                rule.isSkipWeekends(),
                rule.getStartDate(),
                rule.getEndDate(),
                rule.getTargetOccurrenceDate(),
                rule.getNextRunDate(),
                rule.getLastRunDate(),
                rule.getStatus(),
                rule.isAutoPost(),
                rule.getMaxRetries(),
                rule.getRetryCount(),
                rule.getLastError(),
                rule.getFailureReason(),
                rule.getGeneratedCount(),
                rule.getCreatedAt());
    }

    public RecurringRunResponse toRunResponse(RecurringRun run) {
        return new RecurringRunResponse(
                run.getId(),
                run.getScheduledFor(),
                run.getExecutedAt(),
                run.getStatus(),
                run.getTransactionId(),
                run.getAttempts(),
                run.getErrorMessage());
    }

    public OccurrencePreviewResponse.Occurrence toOccurrence(LocalDate date, long estimatedAmountMinor,
            boolean willRun) {
        return new OccurrencePreviewResponse.Occurrence(date, estimatedAmountMinor, willRun);
    }
}
