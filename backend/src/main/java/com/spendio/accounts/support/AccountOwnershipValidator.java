package com.spendio.accounts.support;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * User scoping for every account-level write (ARCHITECTURE.md D.6, E.8): accounts of
 * other users are indistinguishable from missing ones - a 404, never a 403.
 */
@Component
public class AccountOwnershipValidator {

    private final AccountRepository accounts;

    public AccountOwnershipValidator(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Account requireOwned(Long userId, Long accountId) {
        return accounts.findByIdAndUserIdAndDeletedAtIsNull(accountId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found: " + accountId).withProperty("accountId", accountId));
    }
}