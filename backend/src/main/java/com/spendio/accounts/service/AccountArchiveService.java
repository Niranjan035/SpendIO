package com.spendio.accounts.service;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.accounts.support.AccountOwnershipValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archive / restore lifecycle (ARCHITECTURE.md D.6). Archiving freezes an account:
 * it stays visible and counted in balances, but participates in no new writes.
 */
@Service
public class AccountArchiveService {

    private final AccountRepository accounts;
    private final AccountOwnershipValidator ownership;

    public AccountArchiveService(AccountRepository accounts, AccountOwnershipValidator ownership) {
        this.accounts = accounts;
        this.ownership = ownership;
    }

    @Transactional
    public Account archive(Long userId, Long accountId) {
        Account account = ownership.requireOwned(userId, accountId);
        if (!account.isArchived()) {
            account.markArchived();
            account.setPrimary(false);
            return accounts.save(account);
        }
        return account;
    }

    @Transactional
    public Account restore(Long userId, Long accountId) {
        Account account = ownership.requireOwned(userId, accountId);
        if (account.isArchived()) {
            account.markRestored();
            accounts.save(account);
        }
        return account;
    }
}