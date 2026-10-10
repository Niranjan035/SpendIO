package com.spendio.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.analytics.repository.AnalyticsQueryRepository.AccountLine;
import com.spendio.analytics.repository.AnalyticsQueryRepository.CategoryAmount;
import com.spendio.analytics.repository.AnalyticsQueryRepository.DailyFlow;
import com.spendio.analytics.repository.AnalyticsQueryRepository.MonthTotals;
import com.spendio.analytics.repository.AnalyticsQueryRepository.Totals;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.service.CategoryService;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.service.TransactionService;
import com.spendio.transactions.service.TransferService;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.service.ProfileService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every native analytics query against real MySQL (ARCHITECTURE.md Phase 13:
 * "AnalyticsQueryRepositoryIT"). Expectations mirror the balances module's active-ledger
 * filter: non-deleted, non-VOID, {@code opening:} rows excluded, transfers excluded
 * from income/expense, uncategorised rows bucketed under NULL, children folded into
 * their parent bucket when {@code parentOnly}.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class AnalyticsQueryRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AnalyticsQueryRepository query;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private TransactionService transactions;
    @Autowired
    private TransferService transfers;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private EntityManager em;

    private long alice;
    private long bob;
    private long checking;
    private long savings;
    private long bobChecking;
    private long food;
    private long groceries;
    private long dining;
    private long salary;

    @BeforeEach
    void seedFixture() {
        alice = profileService.create(new CreateUserCommand(
                "alice-analytics@spendio.test", "{bcrypt}", "Alice", "Analytics", "UTC", "en-US", "USD")).id();
        bob = profileService.create(new CreateUserCommand(
                "bob-analytics@spendio.test", "{bcrypt}", "Bob", "Analytics", "UTC", "en-US", "USD")).id();

        checking = accountService.create(alice, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 100_000L, LocalDate.of(2026, 1, 1),
                null, null, null, null, null, null, null, null, false, null, false)).getId();
        savings = accountService.create(alice, new CreateAccountRequest(
                "Savings", AccountType.INVESTMENT, "USD", 0, null,
                null, null, null, null, null, null, null, null, false, null, false)).getId();
        bobChecking = accountService.create(bob, new CreateAccountRequest(
                "Bob Checking", AccountType.BANK, "USD", 0, null,
                null, null, null, null, null, null, null, null, false, null, false)).getId();

        food = categoryService.create(alice, new CreateCategoryRequest(
                "Food", "EXPENSE", null, null, "#123456", false)).getId();
        groceries = categoryService.create(alice, new CreateCategoryRequest(
                "Groceries", "EXPENSE", food, null, "#FF0000", false)).getId();
        dining = categoryService.create(alice, new CreateCategoryRequest(
                "Dining", "EXPENSE", food, null, "#00FF00", false)).getId();
        salary = categoryService.create(alice, new CreateCategoryRequest(
                "Salary", "INCOME", null, null, null, false)).getId();
        long bobFood = categoryService.create(bob, new CreateCategoryRequest(
                "Bob Food", "EXPENSE", null, null, null, false)).getId();

        Account ledger = em.getReference(Account.class, checking);

        // The three postable rows that count. (INCOME/EXPENSE rows with no category are
        // impossible: chk_tx_category_required on the transactions table forbids them.)
        transactions.create(alice, new CreateTransactionRequest(
                checking, groceries, TransactionType.EXPENSE, null, 10_00L, "USD", null,
                LocalDate.of(2026, 6, 1), "Groceries", null, null, null, null, null), null);
        transactions.create(alice, new CreateTransactionRequest(
                checking, dining, TransactionType.EXPENSE, null, 20_00L, "USD", null,
                LocalDate.of(2026, 6, 1), "Dinner", null, null, null, null, null), null);
        transactions.create(alice, new CreateTransactionRequest(
                checking, salary, TransactionType.INCOME, null, 100_00L, "USD", null,
                LocalDate.of(2026, 6, 15), "Payday", null, null, null, null, null), null);

        // The rows the active-ledger filter must keep out.
        // chk_tx_void_category forces VOID rows to be category-less ADJUSTMENTs.
        saveRaw(alice, ledger, null, TransactionType.ADJUSTMENT, 99_00L,
                LocalDate.of(2026, 6, 2), TransactionStatus.VOID, null);
        long doomed = saveRaw(alice, ledger, groceries, TransactionType.EXPENSE, 77_00L,
                LocalDate.of(2026, 6, 3), TransactionStatus.POSTED, null);
        transactions.delete(alice, doomed);
        saveRaw(alice, ledger, groceries, TransactionType.EXPENSE, 50_00L,
                LocalDate.of(2026, 6, 5), TransactionStatus.POSTED, "opening:test");

        // A transfer moves money without touching income/expense aggregates.
        transfers.create(alice, new com.spendio.transactions.dto.request.CreateTransferRequest(
                checking, savings, 10_00L, "USD", null, LocalDate.of(2026, 6, 20),
                "Move to savings", null));

        // Bob's world must never leak into Alice's aggregates.
        saveRaw(bob, em.getReference(Account.class, bobChecking), bobFood, TransactionType.EXPENSE, 999_00L,
                LocalDate.of(2026, 6, 10), TransactionStatus.POSTED, null);
    }

    @Test
    void totalsReconcileIncomeExpenseAndCount() {
        Totals june = query.totals(alice, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(june.incomeMinor()).isEqualTo(100_00L);
        assertThat(june.expenseMinor()).isEqualTo(30_00L);
        assertThat(june.transactionCount()).isEqualTo(3);

        Totals empty = query.totals(alice, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        assertThat(empty.incomeMinor()).isZero();
        assertThat(empty.expenseMinor()).isZero();
    }

    @Test
    void dailyFlowBucketsByDayWithTransfersExcluded() {
        List<DailyFlow> june = query.dailyFlow(alice, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(june).hasSize(2)
                .extracting(DailyFlow::day, DailyFlow::incomeMinor, DailyFlow::expenseMinor)
                .containsExactly(
                        tuple(LocalDate.of(2026, 6, 1), 0L, 30_00L),
                        tuple(LocalDate.of(2026, 6, 15), 100_00L, 0L));
    }

    @Test
    void categoryBreakdownOrdersByAmountAndHonoursLimitAndParentOnly() {
        List<CategoryAmount> byCategory = query.byCategory(
                alice, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "EXPENSE", 10, false);
        assertThat(byCategory).extracting(CategoryAmount::name, CategoryAmount::amountMinor,
                        CategoryAmount::categoryId, CategoryAmount::transactionCount)
                .containsExactly(
                        tuple("Dining", 20_00L, dining, 1L),
                        tuple("Groceries", 10_00L, groceries, 1L));

        assertThat(query.byCategory(alice, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                "EXPENSE", 1, false)).hasSize(1);

        List<CategoryAmount> parents = query.byCategory(
                alice, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "EXPENSE", 10, true);
        assertThat(parents).extracting(CategoryAmount::name, CategoryAmount::amountMinor,
                        CategoryAmount::transactionCount)
                .containsExactly(tuple("Food", 30_00L, 2L));
    }

    @Test
    void monthlyTotalsGroupByCalendarMonth() {
        List<MonthTotals> months = query.monthlyTotals(
                alice, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(months).hasSize(1);
        assertThat(months.get(0).year()).isEqualTo(2026);
        assertThat(months.get(0).month()).isEqualTo(6);
        assertThat(months.get(0).incomeMinor()).isEqualTo(100_00L);
        assertThat(months.get(0).expenseMinor()).isEqualTo(30_00L);
    }

    @Test
    void accountLinesCarryOpeningPlusNetWithTheSameExclusions() {
        List<AccountLine> lines = query.accountLines(alice);
        assertThat(lines)
                .extracting(AccountLine::name, AccountLine::openingBalanceMinor, AccountLine::netMinor)
                .containsExactlyInAnyOrder(
                        tuple("Checking", 100_000L, 60_00L),
                        tuple("Savings", 0L, 10_00L));

        assertThat(query.accountLines(bob))
                .extracting(AccountLine::name, AccountLine::netMinor)
                .containsExactly(tuple("Bob Checking", -999_00L));
    }

    private long saveRaw(long userId, Account account, Long categoryId, TransactionType type, long amount,
            LocalDate occurredOn, TransactionStatus status, String dedupeKey) {
        Transaction tx = Transaction.create(
                userId, account,
                categoryId == null ? null : em.getReference(com.spendio.categories.domain.Category.class, categoryId),
                type, TransactionDirection.OUTFLOW, amount, "USD", BigDecimal.ONE, amount, null,
                occurredOn, status, "Raw", null, null, null,
                dedupeKey == null ? "raw-" + System.nanoTime() : dedupeKey);
        return transactionRepository.save(tx).getId();
    }
}