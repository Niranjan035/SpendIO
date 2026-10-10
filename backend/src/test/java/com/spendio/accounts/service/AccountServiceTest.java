package com.spendio.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.dto.request.UpdateAccountRequest;
import com.spendio.accounts.repository.AccountBalanceRepository;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.accounts.support.AccountOwnershipValidator;
import com.spendio.reference.service.CurrencyService;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private AccountBalanceRepository ledger;
    @Mock
    private AccountOwnershipValidator ownership;
    @Mock
    private CurrencyService currencyService;
    @Mock
    private AccountBalanceService balanceService;

    @InjectMocks
    private AccountService service;

    @Test
    void createRejectsDuplicateName() {
        when(accounts.existsByUserIdAndNameAndDeletedAtIsNull(42L, "Current Account")).thenReturn(true);

        assertThatThrownBy(() -> service.create(42L, baseRequest("Current Account", 0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_NAME_DUPLICATE));
    }

    @Test
    void createRejectsSecondPrimary() {
        when(accounts.findFirstByUserIdAndPrimaryAccountTrueAndDeletedAtIsNull(42L))
                .thenReturn(Optional.of(Account.create(42L, "Checking", AccountType.BANK, "USD", 0, null)));

        assertThatThrownBy(() -> service.create(42L, primaryRequest("Second", 0)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_PRIMARY_ALREADY_EXISTS));
    }

    @Test
    void createWithOpeningWritesAnOpeningAdjustmentOnTheOpeningTimeline() {
        when(accounts.save(any())).thenAnswer(inv -> {
            Account saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        service.create(42L, new CreateAccountRequest("Wallet", AccountType.WALLET, "USD", 125_00,
                LocalDate.of(2026, 1, 5), null, null, null, null, null, null, null, null,
                false, LocalDate.of(2026, 1, 10), true));

        ArgumentCaptor<String> direction = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> amount = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDate> occurredOn = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<String> dedupe = ArgumentCaptor.forClass(String.class);
        verify(ledger).insertAdjustment(anyLong(), anyLong(), direction.capture(), amount.capture(),
                anyString(), anyLong(), occurredOn.capture(), anyString(), dedupe.capture());
        assertThat(direction.getValue()).isEqualTo("INFLOW");
        assertThat(amount.getValue()).isEqualTo(125_00L);
        assertThat(occurredOn.getValue()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(dedupe.getValue()).startsWith("opening:");
        // the frozen base amount is resolved on the opening *transaction* date, not today
        verify(balanceService).toBase(42L, 125_00L, "USD", LocalDate.of(2026, 1, 10));
    }

    @Test
    void createWithForeignCurrencyOpeningConvertsToBaseOnTheOpeningDate() {
        when(accounts.save(any())).thenAnswer(inv -> {
            Account saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(balanceService.toBase(42L, 900_00L, "USD", LocalDate.of(2026, 10, 9))).thenReturn(75_123_00L);

        service.create(42L, new CreateAccountRequest("US Broker", AccountType.BANK, "USD", 900_00,
                LocalDate.of(2026, 10, 8), null, null, null, null, null, null, null, null,
                false, LocalDate.of(2026, 10, 9), true));

        ArgumentCaptor<Long> baseAmount = ArgumentCaptor.forClass(Long.class);
        verify(ledger).insertAdjustment(anyLong(), anyLong(), anyString(), anyLong(), anyString(),
                baseAmount.capture(), any(), anyString(), anyString());
        assertThat(baseAmount.getValue()).isEqualTo(75_123_00L);
    }

    @Test
    void createFailsFastWhenTheOpeningBalanceRateIsUnavailable() {
        when(accounts.save(any())).thenAnswer(inv -> {
            Account saved = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(balanceService.toBase(anyLong(), anyLong(), anyString(), any(LocalDate.class)))
                .thenThrow(new BusinessException(ErrorCode.FX_RATE_UNAVAILABLE,
                        "No FX rate available for USD/INR on 2026-10-09"));

        assertThatThrownBy(() -> service.create(42L, new CreateAccountRequest("US Broker",
                AccountType.BANK, "USD", 900_00, LocalDate.of(2026, 10, 8), null, null, null,
                null, null, null, null, null, false, LocalDate.of(2026, 10, 9), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FX_RATE_UNAVAILABLE));
        verify(ledger, never()).insertAdjustment(anyLong(), anyLong(), anyString(), anyLong(),
                anyString(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    void createWithZeroOpeningWritesNoAdjustment() {
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(42L, baseRequest("Cash", 0));

        verify(ledger, never()).insertAdjustment(anyLong(), anyLong(), anyString(),
                anyLong(), anyString(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    void updateRejectsNameOfAnotherOwnAccount() {
        Account account = Account.create(42L, "Wallet", AccountType.WALLET, "USD", 0, null);
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);
        when(accounts.existsByUserIdAndNameAndDeletedAtIsNullAndIdNot(42L, "Cash", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(42L, 1L,
                new UpdateAccountRequest("Cash", null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_NAME_DUPLICATE));
    }

    @Test
    void updateAllowsCurrencyChangeWhileThereAreNoTransactions() {
        Account account = Account.create(42L, "Wallet", AccountType.WALLET, "USD", 0, null);
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(42L, 1L,
                new UpdateAccountRequest(null, "EUR", null, null, null, null, null, null, null, null, null));

        assertThat(account.getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void updateRejectsCurrencyChangeOnceTransactionsExist() {
        Account account = Account.create(42L, "Wallet", AccountType.WALLET, "USD", 0, null);
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);
        when(ledger.countTransactions(42L, 1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.update(42L, 1L,
                new UpdateAccountRequest(null, "EUR", null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_CURRENCY_IMMUTABLE));
    }

    @Test
    void deleteRefusesWhenTransactionsExistUnlessForced() {
        Account account = Account.create(42L, "Checking", AccountType.BANK, "USD", 0, null);
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);
        when(ledger.countTransactions(42L, 1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(42L, 1L, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_HAS_TRANSACTIONS));
        verify(accounts, never()).save(any());
    }

    @Test
    void deleteWithForceSoftDeletesLedgerAndRenamesToFreeTheName() {
        Account account = Account.create(42L, "Card", AccountType.CREDIT_CARD, "USD", 0, null);
        when(ownership.requireOwned(42L, 1L)).thenReturn(account);
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(42L, 1L, true);

        verify(ledger).softDeleteTransactionsFor(42L, 1L);
        assertThat(account.isDeleted()).isTrue();
        assertThat(account.getName()).startsWith("Card (archived ").hasSizeLessThanOrEqualTo(80);
    }

    @Test
    void setPrimaryDemotesEveryOtherPrimary() {
        Account account = Account.create(42L, "Checking", AccountType.BANK, "USD", 0, null);
        when(ownership.requireOwned(42L, 9L)).thenReturn(account);
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setPrimary(42L, 9L);

        verify(accounts).demoteAllOtherPrimaries(42L, 9L);
        assertThat(account.isPrimary()).isTrue();
    }

    @Test
    void setPrimaryOnArchivedAccountFails() {
        Account account = Account.create(42L, "Checking", AccountType.BANK, "USD", 0, null);
        account.markArchived();
        when(ownership.requireOwned(42L, 9L)).thenReturn(account);

        assertThatThrownBy(() -> service.setPrimary(42L, 9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_ARCHIVED));
    }

    private static CreateAccountRequest baseRequest(String name, long openingMinor) {
        return new CreateAccountRequest(name, AccountType.BANK, "USD", openingMinor, null,
                null, null, null, null, null, null, null, null, false, null, true);
    }

    private static CreateAccountRequest primaryRequest(String name, long openingMinor) {
        return new CreateAccountRequest(name, AccountType.BANK, "USD", openingMinor, null,
                null, null, null, null, null, null, null, null, true, null, true);
    }
}