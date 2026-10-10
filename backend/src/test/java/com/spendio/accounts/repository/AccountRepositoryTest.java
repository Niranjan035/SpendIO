package com.spendio.accounts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.support.BalanceLine;
import com.spendio.accounts.support.DailyNet;
import com.spendio.accounts.spec.AccountFilter;
import com.spendio.accounts.spec.AccountSpecifications;
import com.spendio.sharedkernel.domain.enums.AccountType;
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
 * Native balance aggregation and account repositories (ARCHITECTURE.md D.6),
 * exercised against the real spendio_test ledger.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class AccountRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AccountRepository accounts;
    @Autowired
    private AccountBalanceRepository ledger;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void balanceLinesComputeOpeningPlusNetWithTheLedgerExclusions() {
        long userId = seedUser();
        long checking = save(userId, "Checking", AccountType.BANK, 1_000_00, LocalDate.of(2026, 1, 1));
        long card = save(userId, "Card", AccountType.CREDIT_CARD, 0, null);
        insertTx(userId, checking, "INFLOW", 500_00, LocalDate.of(2026, 1, 2), "POSTED", null);
        insertTx(userId, checking, "OUTFLOW", 200_00, LocalDate.of(2026, 1, 3), "POSTED", null);
        insertTx(userId, checking, "INFLOW", 9_999_00, LocalDate.of(2026, 1, 4), "VOID", null);
        insertTx(userId, card, "OUTFLOW", 3_500_00, LocalDate.of(2026, 1, 5), "POSTED", null);
        insertTx(userId, card, "OUTFLOW", 3_500_00, LocalDate.of(2026, 1, 5), "POSTED", "x-card");
        jdbc.update("UPDATE transactions SET deleted_at = NOW(3) WHERE dedupe_key = 'x-card'");

        List<BalanceLine> lines = ledger.balanceLines(userId);

        assertThat(lines).hasSize(2);
        long checkingBalance = lines.stream()
                .filter(l -> l.accountId().equals(checking)).findFirst().orElseThrow().balance();
        long cardBalance = lines.stream()
                .filter(l -> l.accountId().equals(card)).findFirst().orElseThrow().balance();
        assertThat(checkingBalance).isEqualTo(1_000_00 + 500_00 - 200_00);
        assertThat(cardBalance).isEqualTo(-3_500_00);
    }

    @Test
    void dailyNetsAndNetBeforeProduceTheRunningSeriesInputs() {
        long userId = seedUser();
        long id = save(userId, "Checking", AccountType.BANK, 1_000_00, null);
        insertTx(userId, id, "INFLOW", 100_00, LocalDate.of(2026, 1, 2), "POSTED", null);
        insertTx(userId, id, "INFLOW", 250_00, LocalDate.of(2026, 1, 5), "POSTED", null);

        long before = ledger.netBefore(userId, id, LocalDate.of(2026, 1, 3));
        List<DailyNet> nets = ledger.dailyNets(userId, id,
                LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 6));

        assertThat(before).isEqualTo(100_00);
        assertThat(nets).hasSize(1);
        assertThat(nets.getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(nets.getFirst().netMinor()).isEqualTo(250_00);
    }

    @Test
    void transactionCountsIgnoreSoftDeletedRows() {
        long userId = seedUser();
        long id = save(userId, "Checking", AccountType.BANK, 0, null);
        insertTx(userId, id, "INFLOW", 100_00, LocalDate.of(2026, 1, 2), "POSTED", null);
        insertTx(userId, id, "INFLOW", 200_00, LocalDate.of(2026, 1, 3), "POSTED", "keep-me");
        jdbc.update("UPDATE transactions SET deleted_at = NOW(3) WHERE dedupe_key = 'keep-me'");

        assertThat(ledger.transactionCounts(userId).get(id)).isEqualTo(1);
        assertThat(ledger.countTransactions(userId, id)).isEqualTo(1);
    }

    @Test
    void matchingIdsHonoursTheHasTransactionsFilter() {
        long userId = seedUser();
        long used = save(userId, "Used", AccountType.BANK, 0, null);
        long empty = save(userId, "Empty", AccountType.BANK, 0, null);
        insertTx(userId, used, "INFLOW", 100_00, LocalDate.of(2026, 1, 2), "POSTED", null);

        assertThat(ledger.matchingIds(userId, new AccountFilter(null, null, null, null, null, true)))
                .containsExactly(used);
        assertThat(ledger.matchingIds(userId, new AccountFilter(null, null, null, null, null, false)))
                .containsExactly(empty);
    }

    @Test
    void specificationsFilterTheWayTheControllerExpects() {
        long userId = seedUser();
        save(userId, "Checking", AccountType.BANK, 0, null);
        long archivedId = save(userId, "Old", AccountType.WALLET, 0, null);
        Account archived = accounts.findById(archivedId).orElseThrow();
        archived.markArchived();
        accounts.save(archived);

        List<Account> active = accounts.findAll(AccountSpecifications.forFilter(userId,
                new AccountFilter(null, null, null, null, null, null)));
        assertThat(active).hasSize(1);

        List<Account> everything = accounts.findAll(AccountSpecifications.forFilter(userId,
                new AccountFilter(null, null, null, null, true, null)));
        assertThat(everything).extracting(Account::getName).containsExactly("Checking", "Old");

        List<Account> wallets = accounts.findAll(AccountSpecifications.forFilter(userId,
                new AccountFilter(AccountType.WALLET, null, null, null, true, null)));
        assertThat(wallets).extracting(Account::getName).containsExactly("Old");
    }

    @Test
    void demoteAllOtherPrimariesLeavesExactlyOne() {
        long userId = seedUser();
        long aId = save(userId, "A", AccountType.BANK, 0, null);
        long bId = save(userId, "B", AccountType.BANK, 0, null);
        Account a = accounts.findById(aId).orElseThrow();
        Account b = accounts.findById(bId).orElseThrow();
        a.setPrimary(true);
        b.setPrimary(true);
        accounts.save(a);
        accounts.save(b);

        int demoted = accounts.demoteAllOtherPrimaries(userId, b.getId());

        assertThat(demoted).isEqualTo(1);
        Account reloadedA = accounts.findById(aId).orElseThrow();
        assertThat(reloadedA.isPrimary()).isFalse();
        assertThat(accounts.findFirstByUserIdAndPrimaryAccountTrueAndDeletedAtIsNull(userId).orElseThrow().getId())
                .isEqualTo(bId);
    }

    private long save(long userId, String name, AccountType type, long opening, LocalDate openingDate) {
        Account account = Account.create(userId, name, type, "USD", opening, openingDate);
        return accounts.save(account).getId();
    }

    private void insertTx(long userId, long accountId, String direction, long amount, LocalDate day,
            String status, String dedupe) {
        jdbc.update("""
                INSERT INTO transactions
                    (user_id, account_id, type, direction, amount_minor, currency_code, exchange_rate,
                     base_amount_minor, occurred_on, status, description, dedupe_key, created_at, updated_at, version)
                VALUES (?, ?, 'ADJUSTMENT', ?, ?, 'USD', 1, ?, ?, ?, 't', ?, NOW(3), NOW(3), 0)
                """, userId, accountId, direction, amount, amount, day, status, dedupe);
    }

    private long seedUser() {
        UserResponse user = profileService.create(new CreateUserCommand(
                "repo-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "R.", "Test",
                "America/New_York", "en-US", "USD"));
        return user.id();
    }
}