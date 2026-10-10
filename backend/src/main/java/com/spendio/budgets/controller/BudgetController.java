package com.spendio.budgets.controller;

import com.spendio.budgets.dto.request.CreateBudgetRequest;
import com.spendio.budgets.dto.request.UpdateBudgetRequest;
import com.spendio.budgets.dto.response.BudgetAlertResponse;
import com.spendio.budgets.dto.response.BudgetHistoryResponse;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.dto.response.BudgetResponse;
import com.spendio.budgets.service.BudgetAlertService;
import com.spendio.budgets.service.BudgetProgressService;
import com.spendio.budgets.service.BudgetQueryService;
import com.spendio.budgets.service.BudgetService;
import com.spendio.budgets.spec.BudgetFilter;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Budget endpoints (ARCHITECTURE.md D.10, E.10): CRUD, progress and history reads,
 * the alert inbox with acknowledgement, and the forced evaluation used by operators
 * and tests. Alert evaluation also runs after every create/update and nightly.
 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService service;
    private final BudgetQueryService query;
    private final BudgetProgressService progress;
    private final BudgetAlertService alertService;

    public BudgetController(BudgetService service, BudgetQueryService query, BudgetProgressService progress,
            BudgetAlertService alertService) {
        this.service = service;
        this.query = query;
        this.progress = progress;
        this.alertService = alertService;
    }

    @GetMapping
    public PageResponse<BudgetResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) com.spendio.budgets.domain.Budget.Status status,
            @RequestParam(required = false) com.spendio.budgets.domain.Budget.PeriodType periodType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) LocalDate activeInPeriod,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return query.list(userId, new BudgetFilter(status, periodType, categoryId, activeInPeriod), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(@CurrentUserId Long userId,
            @Valid @RequestBody CreateBudgetRequest request) {
        return service.create(userId, request);
    }

    /** Every budget containing today, each carrying its live progress snapshot. */
    @GetMapping("/current")
    public List<BudgetProgressResponse> current(@CurrentUserId Long userId) {
        return progress.current(userId);
    }

    @GetMapping("/alerts")
    public PageResponse<BudgetAlertResponse> alerts(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Boolean unacknowledgedOnly,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return query.alertList(userId, unacknowledgedOnly, from, to, page, size);
    }

    /** Force an evaluation pass (defaults to today's window); returns alerts created. */
    @PostMapping("/evaluate")
    public Map<String, Integer> evaluate(
            @CurrentUserId Long userId,
            @RequestParam(required = false) LocalDate periodStart,
            @RequestParam(required = false) LocalDate periodEnd) {
        LocalDate today = LocalDate.now();
        LocalDate from = periodStart != null ? periodStart : today;
        LocalDate to = periodEnd != null ? periodEnd : today;
        return Map.of("created", alertService.evaluate(userId, from, to));
    }

    @GetMapping("/{id}")
    public BudgetResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return query.detail(userId, id);
    }

    @PatchMapping("/{id}")
    public BudgetResponse update(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody UpdateBudgetRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @GetMapping("/{id}/progress")
    public BudgetProgressResponse budgetProgress(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate periodStart,
            @RequestParam(required = false) LocalDate periodEnd) {
        return progress.progress(userId, id, periodStart, periodEnd);
    }

    @GetMapping("/{id}/history")
    public BudgetHistoryResponse history(@CurrentUserId Long userId, @PathVariable Long id) {
        return query.history(userId, id);
    }

    @PostMapping("/{id}/alerts/{alertId}/acknowledge")
    public BudgetAlertResponse acknowledge(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @PathVariable Long alertId) {
        return query.acknowledge(userId, id, alertId);
    }
}
