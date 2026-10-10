package com.spendio.recurring.controller;

import com.spendio.recurring.dto.request.CreateRecurringRequest;
import com.spendio.recurring.dto.request.UpdateRecurringRequest;
import com.spendio.recurring.dto.response.CreateRecurringResponse;
import com.spendio.recurring.dto.response.OccurrencePreviewResponse;
import com.spendio.recurring.dto.response.RecurringRunResponse;
import com.spendio.recurring.dto.response.RecurringTransactionResponse;
import com.spendio.recurring.service.RecurringQueryService;
import com.spendio.recurring.service.RecurringService;
import com.spendio.recurring.spec.RecurringFilter;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recurring endpoints (ARCHITECTURE.md D.9, E.9): rule CRUD plus the lifecycle
 * transitions and the two execution entry points - {@code run-now} (immediate, honours
 * {@code Idempotency-Key}) and {@code resume} (bounded catch-up, see
 * {@link com.spendio.recurring.service.RecurringExecutionService}).
 */
@RestController
@RequestMapping("/api/v1/recurring-transactions")
public class RecurringTransactionController {

    private final RecurringService service;
    private final RecurringQueryService query;

    public RecurringTransactionController(RecurringService service, RecurringQueryService query) {
        this.service = service;
        this.query = query;
    }

    @GetMapping
    public PageResponse<RecurringTransactionResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) RecurringStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer dueWithinDays,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return query.list(userId, new RecurringFilter(status, type, accountId, categoryId, dueWithinDays),
                page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRecurringResponse create(@CurrentUserId Long userId,
            @Valid @RequestBody CreateRecurringRequest request) {
        return service.create(userId, request);
    }

    @GetMapping("/upcoming")
    public List<RecurringTransactionResponse> upcoming(@CurrentUserId Long userId,
            @RequestParam(required = false) Integer days) {
        return query.upcoming(userId, days);
    }

    @GetMapping("/{id}")
    public RecurringTransactionResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return query.detail(userId, id);
    }

    @PatchMapping("/{id}")
    public RecurringTransactionResponse update(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody UpdateRecurringRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/pause")
    public RecurringTransactionResponse pause(@CurrentUserId Long userId, @PathVariable Long id) {
        return service.pause(userId, id);
    }

    @PostMapping("/{id}/resume")
    public RecurringTransactionResponse resume(@CurrentUserId Long userId, @PathVariable Long id) {
        return service.resume(userId, id);
    }

    @PostMapping("/{id}/cancel")
    public RecurringTransactionResponse cancel(@CurrentUserId Long userId, @PathVariable Long id) {
        return service.cancel(userId, id);
    }

    /** Execute now regardless of {@code next_run_date}; a repeated key replays the run. */
    @PostMapping("/{id}/run-now")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringRunResponse runNow(@CurrentUserId Long userId, @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.runNow(userId, id, idempotencyKey);
    }

    @GetMapping("/{id}/preview")
    public OccurrencePreviewResponse preview(@CurrentUserId Long userId, @PathVariable Long id,
            @RequestParam(required = false) Integer count) {
        return query.preview(userId, id, count);
    }

    @GetMapping("/{id}/runs")
    public PageResponse<RecurringRunResponse> runs(@CurrentUserId Long userId, @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return query.runs(userId, id, page, size);
    }
}
