package com.spendio.goals.controller;

import com.spendio.goals.dto.request.CreateContributionRequest;
import com.spendio.goals.dto.response.GoalContributionResponse;
import com.spendio.goals.service.GoalContributionService;
import com.spendio.goals.service.GoalQueryService;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contribution endpoints (ARCHITECTURE.md D.11, E.11): paged history, deposit /
 * withdrawal creation (optionally transfer-linked), and removal - which reverses the
 * goal's cached total in the same transaction but leaves the ledger alone (reversal
 * is an explicit ledger operation).
 */
@RestController
@RequestMapping("/api/v1/goals/{goalId}/contributions")
public class GoalContributionController {

    private final GoalContributionService service;
    private final GoalQueryService query;

    public GoalContributionController(GoalContributionService service, GoalQueryService query) {
        this.service = service;
        this.query = query;
    }

    @GetMapping
    public PageResponse<GoalContributionResponse> list(
            @CurrentUserId Long userId,
            @PathVariable Long goalId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return query.contributionHistory(userId, goalId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalContributionResponse add(
            @CurrentUserId Long userId,
            @PathVariable Long goalId,
            @Valid @RequestBody CreateContributionRequest request) {
        return service.add(userId, goalId, request);
    }

    @DeleteMapping("/{contributionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @CurrentUserId Long userId,
            @PathVariable Long goalId,
            @PathVariable Long contributionId) {
        service.remove(userId, goalId, contributionId);
    }
}
