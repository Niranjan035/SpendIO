package com.spendio.accounts.export;

import com.spendio.accounts.service.AccountQueryService;
import com.spendio.sharedkernel.export.DataExporter;
import org.springframework.stereotype.Component;

/** Account section of a user's data export (ARCHITECTURE.md Phase 14). */
@Component
public class AccountsDataExporter implements DataExporter {

    private final AccountQueryService accounts;

    public AccountsDataExporter(AccountQueryService accounts) {
        this.accounts = accounts;
    }

    @Override
    public String section() {
        return "accounts";
    }

    @Override
    public Object exportFor(Long userId) {
        return accounts.list(userId, 0, 100, null, null).content();
    }
}
