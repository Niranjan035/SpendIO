package com.spendio.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.accounts.support.AccountOwnershipValidator;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountArchiveServiceTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private AccountOwnershipValidator ownership;

    @InjectMocks
    private AccountArchiveService service;

    @Test
    void archiveFreezesAndDemotesThePrimaryFlag() {
        Account account = Account.create(42L, "Checking", AccountType.BANK, "USD", 0, null);
        account.setPrimary(true);
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.archive(42L, 1L);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ARCHIVED);
        assertThat(account.getArchivedAt()).isNotNull();
        assertThat(account.isPrimary()).isFalse();
    }

    @Test
    void archivingAnArchivedAccountIsIdempotent() {
        Account account = Account.create(42L, "Checking", AccountType.BANK, "USD", 0, null);
        account.markArchived();
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);

        service.archive(42L, 1L);

        verify(accounts, never()).save(any());
    }

    @Test
    void restoreReactivates() {
        Account account = Account.create(42L, "Checking", AccountType.BANK, "USD", 0, null);
        account.markArchived();
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);

        service.restore(42L, 1L);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getArchivedAt()).isNull();
    }
}