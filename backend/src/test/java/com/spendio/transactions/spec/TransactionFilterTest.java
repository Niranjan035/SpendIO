package com.spendio.transactions.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.domain.Account;
import com.spendio.accounts.service.AccountService;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.service.CategoryService;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.support.TransactionSortSupport;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.service.ProfileService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger filter matrix (ARCHITECTURE.md E.6): every optional field of
 * {@link TransactionFilter} AND-ed over a fixed six-row fixture, including the
 * categoryless adjustment row and the reversal flag.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class TransactionFilterTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private EntityManager em;

    private long userId;
    private long usdAccount;
    private long eurAccount;
    private long food;
    private long salary;
    private long t1;
    private long t2;
    private long t3;
    private long t4;
    private long t5;
    private long t6;

    @BeforeEach
    void seedFixture() {
        userId = profileService.create(new CreateUserCommand(
                "filter@spendio.test", "{bcrypt}", "Fiona", "Filter", "UTC", "en-US", "USD")).id();
        usdAccount = seedAccount("Checking", "USD");
        eurAccount = seedAccount("Euro Wallet", "EUR");
        food = seedCategory("Groceries", "EXPENSE");
        salary = seedCategory("Salary", "INCOME");

        Account usd = em.getReference(Account.class, usdAccount);
        Account eur = em.getReference(Account.class, eurAccount);
        Category foodCategory = em.getReference(Category.class, food);
        Category salaryCategory = em.getReference(Category.class, salary);

        t1 = save(usd, foodCategory, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 1_000L, "USD",
                1_000L, LocalDate.of(2026, 6, 1), TransactionStatus.POSTED,
                "Coffee beans", "Blue Bottle", "note-a");
        t2 = save(usd, salaryCategory, TransactionType.INCOME, TransactionDirection.INFLOW, 50_000L, "USD",
                50_000L, LocalDate.of(2026, 6, 2), TransactionStatus.POSTED, "Paycheck", null, null);
        t3 = save(eur, foodCategory, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 2_500L, "EUR",
                2_750L, LocalDate.of(2026, 6, 3), TransactionStatus.POSTED,
                "Boulangerie", "Boulangerie", null);
        t4 = save(usd, null, TransactionType.ADJUSTMENT, TransactionDirection.INFLOW, 700L, "USD",
                700L, LocalDate.of(2026, 6, 4), TransactionStatus.POSTED, "Found money", null, null);
        t5 = save(usd, null, TransactionType.ADJUSTMENT, TransactionDirection.OUTFLOW, 999L, "USD",
                999L, LocalDate.of(2026, 6, 5), TransactionStatus.VOID, "Refund pending", null, null);
        t6 = save(usd, foodCategory, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 50L, "USD",
                50L, LocalDate.of(2026, 6, 6), TransactionStatus.POSTED, "Bus fare", null, null);
        Transaction tx6 = transactions.findById(t6).orElseThrow();
        ReflectionTestUtils.setField(tx6, "reversedAt", Instant.now());
        transactions.save(tx6);
    }

    @Test
    void noFilterReturnsEveryLiveRow() {
        assertThat(ids(TransactionFilter.none())).containsExactlyInAnyOrder(t1, t2, t3, t4, t5, t6);
    }

    @Test
    void filtersByTypeDirectionAndStatus() {
        assertThat(ids(with(null, null, null, null, List.of(TransactionType.EXPENSE), null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactlyInAnyOrder(t1, t3, t6);
        assertThat(ids(with(null, null, null, null, null, List.of(TransactionDirection.INFLOW), null,
                null, null, null, null, null, null, null, null)))
                .containsExactlyInAnyOrder(t2, t4);
        assertThat(ids(with(null, null, null, null, null, null, List.of(TransactionStatus.VOID),
                null, null, null, null, null, null, null, null)))
                .containsExactly(t5);
    }

    @Test
    void filtersByAccountCategoryAndCategoryType() {
        assertThat(ids(with(null, List.of(eurAccount), null, null, null, null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactly(t3);
        assertThat(ids(with(null, null, List.of(salary), null, null, null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactly(t2);
        assertThat(ids(with(null, null, null, Category.Type.EXPENSE, null, null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactlyInAnyOrder(t1, t3, t6);
        assertThat(ids(with(null, List.of(eurAccount), null, null, List.of(TransactionType.EXPENSE),
                null, null, null, null, null, null, null, null, null, null)))
                .containsExactly(t3);
    }

    @Test
    void filtersByDateRangeAndAmountBand() {
        assertThat(ids(with(null, null, null, null, null, null, null,
                LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 4), null, null, null, null, null, null)))
                .containsExactlyInAnyOrder(t2, t3, t4);
        assertThat(ids(with(null, null, null, null, null, null, null,
                null, null, 5_000L, null, null, null, null, null)))
                .containsExactly(t2);
        assertThat(ids(with(null, null, null, null, null, null, null,
                null, null, null, 1_000L, null, null, null, null)))
                .containsExactlyInAnyOrder(t1, t4, t5, t6);
    }

    @Test
    void normalisesTheCurrencyCodeAndHonoursBaseCurrencyOnly() {
        assertThat(ids(with(null, null, null, null, null, null, null,
                null, null, null, null, "  eur ", null, null, null)))
                .containsExactly(t3);
        assertThat(ids(with(null, null, null, null, null, null, null,
                null, null, null, null, null, Boolean.TRUE, null, null)))
                .containsExactlyInAnyOrder(t1, t2, t4, t5, t6);
    }

    @Test
    void textSearchCoversDescriptionMerchantAndNotes() {
        assertThat(ids(with("COFFEE", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactly(t1);
        assertThat(ids(with("bottle", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactly(t1);
        assertThat(ids(with("note-a", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactly(t1);
        assertThat(ids(with("boulangerie", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null)))
                .containsExactly(t3);
    }

    @Test
    void flagsNotesAndReversals() {
        assertThat(ids(with(null, null, null, null, null, null, null,
                null, null, null, null, null, null, Boolean.TRUE, null)))
                .containsExactly(t1);
        assertThat(ids(with(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, Boolean.TRUE)))
                .containsExactly(t6);
    }

    @Test
    void categorySortKeepsCategorylessAdjustmentRows() {
        List<Transaction> sorted = transactions.findAll(fullSpec(),
                TransactionSortSupport.pageable(0, 10, "category,asc")).getContent();

        assertThat(sorted).hasSize(6);
        assertThat(sorted).extracting(Transaction::getId).contains(t4);
    }

    @Test
    void amountSortTranslatesToTheColumnPath() {
        List<Transaction> sorted = transactions.findAll(fullSpec(),
                TransactionSortSupport.pageable(0, 10, "amount,desc")).getContent();

        assertThat(sorted).extracting(Transaction::getId)
                .containsExactly(t2, t3, t1, t5, t4, t6);
    }

    // --- helpers --------------------------------------------------------------

    private org.springframework.data.jpa.domain.Specification<Transaction> fullSpec() {
        return TransactionSpecifications.withReferences()
                .and(TransactionSpecifications.forFilter(userId, TransactionFilter.none(), "USD"));
    }

    private Set<Long> ids(TransactionFilter filter) {
        return transactions.findAll(TransactionSpecifications.withReferences()
                        .and(TransactionSpecifications.forFilter(userId, filter, "USD")))
                .stream()
                .map(Transaction::getId)
                .collect(Collectors.toSet());
    }

    private static TransactionFilter with(String q, List<Long> accountIds, List<Long> categoryIds,
            Category.Type categoryType, List<TransactionType> types, List<TransactionDirection> directions,
            List<TransactionStatus> statuses, LocalDate from, LocalDate to, Long minAmountMinor,
            Long maxAmountMinor, String currencyCode, Boolean baseCurrencyOnly, Boolean hasNotes,
            Boolean hasReversal) {
        return new TransactionFilter(q, accountIds, categoryIds, categoryType, types, directions, statuses,
                from, to, minAmountMinor, maxAmountMinor, currencyCode, baseCurrencyOnly, null, null,
                hasNotes, hasReversal);
    }

    private long seedAccount(String name, String currency) {
        return accountService.create(userId, new CreateAccountRequest(
                name, AccountType.BANK, currency, 0, null, null, null, null, null, null, null, null,
                null, false, null, true)).getId();
    }

    private long seedCategory(String name, String type) {
        return categoryService.create(userId, new CreateCategoryRequest(name, type, null, null, null, false))
                .getId();
    }

    private long save(Account account, Category category, TransactionType type, TransactionDirection direction,
            long amountMinor, String currency, long baseAmountMinor, LocalDate occurredOn,
            TransactionStatus status, String description, String merchant, String notes) {
        Transaction tx = Transaction.create(userId, account, category, type, direction, amountMinor, currency,
                BigDecimal.ONE, baseAmountMinor, null, occurredOn, status, description, merchant, null, notes,
                null);
        return transactions.save(tx).getId();
    }
}
