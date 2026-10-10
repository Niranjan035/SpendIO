package com.spendio.analytics.service;

import com.spendio.analytics.dto.response.GoalProgressSummaryResponse;
import com.spendio.goals.dto.response.GoalResponse;
import com.spendio.goals.dto.response.GoalSummaryResponse;
import com.spendio.goals.service.GoalQueryService;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Goal progress for the dashboard and analytics page (ARCHITECTURE.md D.14
 * {@code GET /analytics/goal-progress}): active goals with target/current and the
 * on-track verdict the goals module already computes (D.11).
 */
@Service
public class GoalAnalyticsService {

    private final GoalQueryService goals;
    private final AnalyticsCacheManager cache;

    public GoalAnalyticsService(GoalQueryService goals, AnalyticsCacheManager cache) {
        this.goals = goals;
        this.cache = cache;
    }

    public List<GoalProgressSummaryResponse> progress(Long userId) {
        return cache.get(userId, "goal-progress", List.class, () -> {
            GoalSummaryResponse summary = goals.summary(userId);
            List<GoalProgressSummaryResponse> result = new ArrayList<>();
            for (GoalResponse goal : summary.goals()) {
                if (goal.status() != GoalStatus.ACTIVE) {
                    continue;
                }
                result.add(new GoalProgressSummaryResponse(
                        goal.id(),
                        goal.name(),
                        goal.targetAmountMinor(),
                        goal.currentAmountMinor(),
                        goal.progressPct(),
                        Boolean.TRUE.equals(goal.onTrack()),
                        goal.currencyCode()));
            }
            return result;
        });
    }
}
