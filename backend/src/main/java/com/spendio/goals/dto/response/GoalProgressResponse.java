package com.spendio.goals.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Full goal progress view (ARCHITECTURE.md D.11/E.11): the goal itself, recent
 * contributions, a cumulative chart series and the milestone crossings - each
 * milestone records when history first reached it (null while uncrossed), which is
 * also what makes milestone notifications fire exactly once.
 */
public record GoalProgressResponse(
        GoalResponse goal,
        List<GoalContributionResponse> contributions,
        List<ChartPoint> chart,
        List<MilestoneStatus> milestones) {

    /** One bucket of the cumulative series: money added in the period and the running total. */
    public record ChartPoint(LocalDate period, long addedMinor, long cumulativeMinor) {
    }

    public record MilestoneStatus(short pct, long amountMinor, LocalDate crossedAt, boolean crossed) {
    }
}
