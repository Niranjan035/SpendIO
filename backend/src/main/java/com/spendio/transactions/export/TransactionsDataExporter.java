package com.spendio.transactions.export;

import com.spendio.sharedkernel.export.DataExporter;
import com.spendio.transactions.service.TransactionQueryService;
import org.springframework.stereotype.Component;

/**
 * Transactions section (ARCHITECTURE.md Phase 14). Reuses the ledger's existing CSV
 * writer so the export carries exactly what the transactions screen downloads, and
 * streams every page rather than a capped window.
 */
@Component
public class TransactionsDataExporter implements DataExporter {

    private final TransactionQueryService transactions;

    public TransactionsDataExporter(TransactionQueryService transactions) {
        this.transactions = transactions;
    }

    @Override
    public String section() {
        return "transactions";
    }

    @Override
    public Object exportFor(Long userId) {
        return java.util.Map.of("csv", transactions.export(userId, null, null));
    }
}
