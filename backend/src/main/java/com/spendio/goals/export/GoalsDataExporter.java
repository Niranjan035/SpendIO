package com.spendio.goals.export;

import com.spendio.goals.service.GoalQueryService;
import com.spendio.sharedkernel.export.DataExporter;
import org.springframework.stereotype.Component;

/** Contributes the user's goals to the data export (ARCHITECTURE.md Phase 14). */
@Component
public class GoalsDataExporter implements DataExporter {

    private final GoalQueryService goals;

    public GoalsDataExporter(GoalQueryService goals) {
        this.goals = goals;
    }

    @Override
    public String section() {
        return "goals";
    }

    @Override
    public Object exportFor(Long userId) {
        return goals.list(userId, null, null, 0, 100).content();
    }
}
