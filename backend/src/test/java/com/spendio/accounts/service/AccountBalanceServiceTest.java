package com.spendio.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.dto.response.AccountBalanceResponse;
import com.spendio.accounts.dto.response.AccountTotalsResponse;
import com.spendio.accounts.dto.response.DailyBalancePointResponse;
import com.spendio.accounts.support.AccountOwnershipValidator;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Balance arithmetic over the real ledger (ARCHITECTURE.md K.2): opening balance,
 * in/out moves, and the void / soft-delete / opening-adjustment exclusions.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class AccountBalanceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AccountBalanceService service;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountArchiveService archiveService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void currentEqualsOpeningForANewAccount() {
        long userId = seedUser();
        long id = accountService.create(userId, account("Checking", 10_000_00,
                LocalDate.of(2026, 1, 1), AccountType.BANK)).getId();

        AccountBalanceResponse balance = service.current(userId, id);

        assertThat(balance.balanceMinor()).isEqualTo(10_000_00);
        assertThat(balance.baseCurrencyCode()).isEqualTo("USD");
        assertThat(balance.balanceBaseMinor()).isEqualTo(10_000_00);
    }

    @Test
    void balanceIsOpeningPlusOnlyTheLiveNonOpeningRows() {
        long userId = seedUser();
        long id = accountService.create(userId, account("Checking", 1_000_00,
                LocalDate.of(2026, 1, 1), AccountType.BANK)).getId();

        inflow(userId, id, 500_00, LocalDate.of(2026, 1, 2), "POSTED", "salary", null);
        outflow(userId, id, 200_00, LocalDate.of(2026, 1, 3), "POSTED", "rent", null);
        inflow(userId, id, 9_999_00, LocalDate.of(2026, 1, 4), "VOID", "voided", null);
        inflow(userId, id, 9_999_00, LocalDate.of(2026, 1, 5), "POSTED", "deleted", null);
        softDelete(userId, id, 9_999_00, LocalDate.of(2026, 1, 5));

        AccountBalanceResponse balance = service.current(userId, id);

        assertThat(balance.balanceMinor()).isEqualTo(1_000_00 + 500_00 - 200_00);
    }

    @Test
    void dailyBalancesAreCumulativeAndCoverGapDays() {
        long userId = seedUser();
        long id = accountService.create(userId, account("Checking", 1_000_00,
                LocalDate.of(2026, 1, 1), AccountType.BANK)).getId();
        inflow(userId, id, 100_00, LocalDate.of(2026, 1, 2), "POSTED", "x", null);
        inflow(userId, id, 250_00, LocalDate.of(2026, 1, 5), "POSTED", "y", null);

        List<DailyBalancePointResponse> series = service.dailyBalances(userId, id,
                LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 5));

        assertThat(series).hasSize(3);
        assertThat(series.get(0).balanceMinor()).isEqualTo(1_100_00);
        assertThat(series.get(1).balanceMinor()).isEqualTo(1_100_00);
        assertThat(series.get(2).balanceMinor()).isEqualTo(1_350_00);
    }

    @Test
    void adjustRebalancesAndIsIdempotentAtTheSameTarget() {
        long userId = seedUser();
        long id = accountService.create(userId, account("Checking", 5_000_00,
                LocalDate.of(2026, 1, 1), AccountType.BANK)).getId();
        long rowsBefore = ledgerCount(userId, id);

        AccountBalanceResponse adjusted = service.adjust(userId, id,
                new com.spendio.accounts.dto.request.AdjustBalanceRequest(2_000_00,
                        LocalDate.of(2026, 2, 1), "closing a deal"));

        assertThat(adjusted.balanceMinor()).isEqualTo(2_000_00);
        assertThat(ledgerCount(userId, id)).isEqualTo(rowsBefore + 1);

        service.adjust(userId, id,
                new com.spendio.accounts.dto.request.AdjustBalanceRequest(2_000_00,
                        LocalDate.of(2026, 2, 2), "no movement"));
        assertThat(ledgerCount(userId, id)).isEqualTo(rowsBefore + 1);
    }

    @Test
    void adjustOnAnArchivedAccountIsRejected() {
        long userId = seedUser();
        long id = accountService.create(userId, account("Checking", 0, null, AccountType.BANK)).getId();
        archiveService.archive(userId, id);

        assertThatThrownBy(() -> service.adjust(userId, id,
                new com.spendio.accounts.dto.request.AdjustBalanceRequest(10,
                        LocalDate.of(2026, 2, 1), "x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_ARCHIVED));
    }

    @Test
    void totalsRollUpEveryAccountIntoTheBaseCurrency() {
        long userId = seedUser();
        accountService.create(userId, account("Checking", 5_000_00,
                LocalDate.of(2026, 1, 1), AccountType.BANK));
        long savings = accountService.create(userId, account("Savings", 0, null, AccountType.BANK)).getId();
        service.adjust(userId, savings,
                new com.spendio.accounts.dto.request.AdjustBalanceRequest(2_500_00,
                        LocalDate.of(2026, 2, 1), "bonus"));

        AccountTotalsResponse totals = service.totals(userId);

        assertThat(totals.totalBaseMinor()).isEqualTo(7_500_00);
        assertThat(totals.baseCurrencyCode()).isEqualTo("USD");
        assertThat(totals.byType().get(AccountType.BANK)).isEqualTo(7_500_00);
        assertThat(totals.byCurrency().get("USD")).isEqualTo(7_500_00);
    }

    @Test
    void crossCurrencyWithoutAnFxImplementationFailsFast() {
        long userId = seedUser();
        long id = accountService.create(userId, new CreateAccountRequest(
                "Travel", AccountType.BANK, "EUR", 0, null, null, null,
                null, null, null, null, null, null, false, null, true)).getId();

        assertThatThrownBy(() -> service.adjust(userId, id,
                new com.spendio.accounts.dto.request.AdjustBalanceRequest(5_000_00,
                        LocalDate.of(2026, 2, 1), "x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FX_RATE_UNAVAILABLE));
    }

    private CreateAccountRequest account(String name, long openingMinor, LocalDate openingDate, AccountType type) {
        return new CreateAccountRequest(name, type, "USD", openingMinor, openingDate,
                null, null, null, null, null, null, null, null, false, null, true);
    }

    private void inflow(long userId, long accountId, long amount, LocalDate day, String status, String desc, String dedupe) {
        insertLedger(userId, accountId, "ADJUSTMENT", "INFLOW", amount, day, status, desc, dedupe);
    }

    private void outflow(long userId, long accountId, long amount, LocalDate day, String status, String desc, String dedupe) {
        insertLedger(userId, accountId, "ADJUSTMENT", "OUTFLOW", amount, day, status, desc, dedupe);
    }

    private void softDelete(long userId, long accountId, long amount, LocalDate day) {
        jdbc.update("UPDATE transactions SET deleted_at = NOW(3) WHERE user_id = ? AND account_id = ? AND occurred_on = ?",
                userId, accountId, day);
    }

    private void insertLedger(long userId, long accountId, String type, String direction, long amount,
            LocalDate day, String status, String desc, String dedupe) {
        jdbc.update("""
                INSERT INTO transactions
                    (user_id, account_id, type, direction, amount_minor, currency_code, exchange_rate,
                     base_amount_minor, occurred_on, status, description, dedupe_key, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'USD', 1, ?, ?, ?, ?, ?, NOW(3), NOW(3), 0)
                """, userId, accountId, type, direction, amount, amount, day, status, desc, dedupe);
    }

    private long ledgerCount(long userId, long accountId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ? AND account_id = ?",
                Long.class, userId, accountId);
    }

    private long seedUser() {
        UserResponse user = profileService.create(new CreateUserCommand(
                "bal-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "B.", "Test",
                "America/New_York", "en-US", "USD"));
        return user.id();
    }
}