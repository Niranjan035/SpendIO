package com.spendio.budgets.export;

import com.spendio.budgets.service.BudgetQueryService;
import com.spendio.sharedkernel.export.DataExporter;
import org.springframework.stereotype.Component;

/** Contributes the user's budgets to the data export (ARCHITECTURE.md Phase 14). */
@Component
public class BudgetsDataExporter implements DataExporter {

    private final BudgetQueryService budgets;

    public BudgetsDataExporter(BudgetQueryService budgets) {
        this.budgets = budgets;
    }

    @Override
    public String section() {
        return "budgets";
    }

    @Override
    public Object exportFor(Long userId) {
        return budgets.list(userId, null, 0, 100).content();
    }
}
