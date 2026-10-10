package com.spendio.goals.controller;

import com.spendio.goals.dto.request.CreateGoalRequest;
import com.spendio.goals.dto.request.UpdateGoalRequest;
import com.spendio.goals.dto.response.GoalProgressResponse;
import com.spendio.goals.dto.response.GoalResponse;
import com.spendio.goals.dto.response.GoalSummaryResponse;
import com.spendio.goals.service.GoalQueryService;
import com.spendio.goals.service.GoalService;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Goal endpoints (ARCHITECTURE.md D.11, E.11): CRUD with the dashboard summary,
 * the explicit complete/reopen transitions and the progress view (chart, milestones,
 * required-per-month). Contribution endpoints live in
 * {@link GoalContributionController}.
 */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService service;
    private final GoalQueryService query;

    public GoalController(GoalService service, GoalQueryService query) {
        this.service = service;
        this.query = query;
    }

    @GetMapping
    public PageResponse<GoalResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) GoalStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return query.list(userId, status, q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateGoalRequest request) {
        return service.create(userId, request);
    }

    @GetMapping("/summary")
    public GoalSummaryResponse summary(@CurrentUserId Long userId) {
        return query.summary(userId);
    }

    @GetMapping("/{id}")
    public GoalResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return query.detail(userId, id);
    }

    @PatchMapping("/{id}")
    public GoalResponse update(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody UpdateGoalRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/complete")
    public GoalResponse complete(@CurrentUserId Long userId, @PathVariable Long id) {
        return service.complete(userId, id);
    }

    @PostMapping("/{id}/reopen")
    public GoalResponse reopen(@CurrentUserId Long userId, @PathVariable Long id) {
        return service.reopen(userId, id);
    }

    @GetMapping("/{id}/progress")
    public GoalProgressResponse progress(
            @CurrentUserId Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) GoalQueryService.Granularity granularity) {
        return query.progress(userId, id, granularity);
    }
}
