package com.spendio.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryService;
import com.spendio.fx.domain.FxRate;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.CheckDuplicateRequest;
import com.spendio.transactions.dto.response.DuplicateCheckResponse;
import com.spendio.transactions.dto.response.DuplicateCheckResponse.Confidence;
import com.spendio.transactions.dto.response.TransactionFilterOptionsResponse;
import com.spendio.transactions.dto.response.TransactionFilterOptionsResponse.AccountOption;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.dto.response.TransactionSummaryResponse;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.spec.TransactionFilter;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.service.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger reads (ARCHITECTURE.md E.6): pagination, sort whitelist translation, detail
 * scoping, hand-computed summary totals, filter facets, CSV export and the duplicate
 * confidence levels.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class TransactionQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionQueryService query;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private com.spendio.fx.repository.FxRateRepository fxRates;
    @Autowired
    private EntityManager em;

    private long userId;
    private long usdAccount;
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
                "query@spendio.test", "{bcrypt}", "Quinn", "Query", "UTC", "en-US", "USD")).id();
        usdAccount = seedAccount(userId, "Checking", "USD");
        long eurAccount = seedAccount(userId, "Euro Wallet", "EUR");
        food = seedCategory(userId, "Groceries", "EXPENSE");
        salary = seedCategory(userId, "Salary", "INCOME");

        Account usd = em.getReference(Account.class, usdAccount);
        Account eur = em.getReference(Account.class, eurAccount);
        Category foodCategory = em.getReference(Category.class, food);
        Category salaryCategory = em.getReference(Category.class, salary);

        t1 = save(usd, foodCategory, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 1_000L, "USD",
                1_000L, LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "Coffee beans", "Blue Bottle");
        t2 = save(usd, salaryCategory, TransactionType.INCOME, TransactionDirection.INFLOW, 50_000L, "USD",
                50_000L, LocalDate.of(2026, 6, 2), TransactionStatus.POSTED, "Paycheck", null);
        t3 = save(eur, foodCategory, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 2_500L, "EUR",
                2_750L, LocalDate.of(2026, 6, 3), TransactionStatus.POSTED, "Boulangerie", null);
        t4 = save(usd, null, TransactionType.ADJUSTMENT, TransactionDirection.INFLOW, 700L, "USD",
                700L, LocalDate.of(2026, 6, 4), TransactionStatus.POSTED, "Found money", null);
        t5 = save(usd, null, TransactionType.ADJUSTMENT, TransactionDirection.OUTFLOW, 999L, "USD",
                999L, LocalDate.of(2026, 6, 5), TransactionStatus.VOID, "Refund pending", null);
        t6 = save(usd, foodCategory, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 50L, "USD",
                50L, LocalDate.of(2026, 6, 6), TransactionStatus.POSTED, "Bus fare", null);
    }

    @Test
    void searchPaginatesWithNewestFirstAsTheDefaultOrder() {
        PageResponse<TransactionResponse> page = query.search(userId, 0, 2, null, TransactionFilter.none());

        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).occurredOn()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(page.content().get(0).description()).isEqualTo("Bus fare");
    }

    @Test
    void searchTranslatesTheAmountSortToItsColumn() {
        PageResponse<TransactionResponse> page = query.search(userId, 0, 3, "amount,asc",
                TransactionFilter.none());

        assertThat(page.content()).extracting(TransactionResponse::amountMinor)
                .containsExactly(50L, 700L, 999L);
    }

    @Test
    void searchRejectsAFieldOutsideTheSortWhitelist() {
        assertThatThrownBy(() -> query.search(userId, 0, 10, "password,asc", TransactionFilter.none()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("sort");
                });
    }

    @Test
    void sortingByCategoryKeepsCategorylessRowsInTheResult() {
        PageResponse<TransactionResponse> page = query.search(userId, 0, 10, "category,asc",
                TransactionFilter.none());

        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.content()).extracting(TransactionResponse::id).contains(t4);
    }

    @Test
    void detailMapsTheRowAndScopesItToTheOwner() {
        TransactionResponse response = query.detail(userId, t1);

        assertThat(response.id()).isEqualTo(t1);
        assertThat(response.description()).isEqualTo("Coffee beans");
        assertThat(response.account().name()).isEqualTo("Checking");
        assertThat(response.account().currencyCode()).isEqualTo("USD");
        assertThat(response.category().name()).isEqualTo("Groceries");
        assertThat(response.baseCurrencyCode()).isEqualTo("USD");
        assertThat(response.baseAmountMinor()).isEqualTo(1_000L);

        long stranger = profileService.create(new CreateUserCommand(
                "stranger-query@spendio.test", "{bcrypt}", "Sam", "Stranger", "UTC", "en-US", "USD")).id();
        long strangerTx = save(em.getReference(Account.class, seedAccount(stranger, "Stranger", "USD")), null,
                TransactionType.ADJUSTMENT, TransactionDirection.INFLOW, 10L, "USD", 10L,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "Not yours", null, stranger);

        assertThatThrownBy(() -> query.detail(userId, strangerTx))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND));
        assertThatThrownBy(() -> query.detail(userId, 999_999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    @Test
    void summaryCountsEveryRowButOnlySumsLiveCashFlow() {
        TransactionSummaryResponse totals = query.summary(userId, TransactionFilter.none());

        assertThat(totals.count()).isEqualTo(6);
        assertThat(totals.incomeBaseMinor()).isEqualTo(50_000L);
        assertThat(totals.expenseBaseMinor()).isEqualTo(1_000L + 2_750L + 50L);
        assertThat(totals.netBaseMinor()).isEqualTo(50_000L - 3_800L);
        assertThat(totals.baseCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void summaryHonoursTheFilter() {
        TransactionSummaryResponse expenses = query.summary(userId,
                new TransactionFilter(null, null, null, null, List.of(TransactionType.EXPENSE), null, null,
                        null, null, null, null, null, null, null, null, null, null));

        assertThat(expenses.count()).isEqualTo(3);
        assertThat(expenses.incomeBaseMinor()).isZero();
        assertThat(expenses.expenseBaseMinor()).isEqualTo(3_800L);
        assertThat(expenses.netBaseMinor()).isEqualTo(-3_800L);
    }

    @Test
    void optionsExposeAccountsCategoriesCurrenciesAndBounds() {
        FxRate rate = new FxRate();
        rate.setBaseCurrencyCode("USD");
        rate.setQuoteCurrencyCode("EUR");
        rate.setRate(new BigDecimal("0.9"));
        rate.setRateDate(LocalDate.of(2026, 6, 1));
        rate.setProvider("manual");
        rate.setManual(true);
        fxRates.save(rate);

        TransactionFilterOptionsResponse options = query.options(userId);

        assertThat(options.accounts()).extracting(AccountOption::name)
                .containsExactlyInAnyOrder("Checking", "Euro Wallet");
        assertThat(options.accounts()).extracting(AccountOption::currencyCode)
                .containsExactlyInAnyOrder("USD", "EUR");
        assertThat(options.categories()).extracting(CategoryResponse::name)
                .contains("Groceries", "Salary");
        assertThat(options.currencies()).containsExactlyInAnyOrder("EUR", "USD");
        assertThat(options.minDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(options.maxDate()).isEqualTo(LocalDate.of(2026, 6, 6));
        assertThat(options.typeCounts())
                .containsEntry(TransactionType.EXPENSE, 3L)
                .containsEntry(TransactionType.INCOME, 1L)
                .containsEntry(TransactionType.ADJUSTMENT, 2L)
                .containsEntry(TransactionType.TRANSFER, 0L);
    }

    @Test
    void exportWritesRfc4180CsvWithEveryRow() {
        String csv = query.export(userId, TransactionFilter.none(), null);

        assertThat(csv).startsWith("id,occurredOn,type,direction,amountMinor,currencyCode,");
        assertThat(csv).contains("Coffee beans").contains("Paycheck").contains("Found money");
        assertThat(csv.split("\r\n")).hasSize(7);
    }

    @Test
    void checkDuplicateDistinguishesHighLowAndNone() {
        DuplicateCheckResponse high = query.checkDuplicate(userId,
                new CheckDuplicateRequest(usdAccount, LocalDate.of(2026, 6, 1), 1_000L, "USD", "Coffee beans"));
        assertThat(high.duplicate()).isTrue();
        assertThat(high.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(high.matchedTransactionId()).isEqualTo(t1);

        DuplicateCheckResponse low = query.checkDuplicate(userId,
                new CheckDuplicateRequest(usdAccount, LocalDate.of(2026, 6, 1), 1_000L, "usd", "Taxi ride"));
        assertThat(low.duplicate()).isFalse();
        assertThat(low.confidence()).isEqualTo(Confidence.LOW);
        assertThat(low.matchedTransactionId()).isNull();

        DuplicateCheckResponse none = query.checkDuplicate(userId,
                new CheckDuplicateRequest(usdAccount, LocalDate.of(2026, 6, 1), 77_777L, "USD", "Anything"));
        assertThat(none.duplicate()).isFalse();
        assertThat(none.confidence()).isEqualTo(Confidence.NONE);
    }

    // --- helpers --------------------------------------------------------------

    private long seedAccount(long owner, String name, String currency) {
        return accountService.create(owner, new CreateAccountRequest(
                name, AccountType.BANK, currency, 0, null, null, null, null, null, null, null, null,
                null, false, null, true)).getId();
    }

    private long seedCategory(long owner, String name, String type) {
        return categoryService.create(owner, new CreateCategoryRequest(name, type, null, null, null, false))
                .getId();
    }

    private long save(Account account, Category category, TransactionType type, TransactionDirection direction,
            long amountMinor, String currency, long baseAmountMinor, LocalDate occurredOn,
            TransactionStatus status, String description, String merchant) {
        return save(account, category, type, direction, amountMinor, currency, baseAmountMinor, occurredOn,
                status, description, merchant, userId);
    }

    private long save(Account account, Category category, TransactionType type, TransactionDirection direction,
            long amountMinor, String currency, long baseAmountMinor, LocalDate occurredOn,
            TransactionStatus status, String description, String merchant, long owner) {
        Transaction tx = Transaction.create(owner, account, category, type, direction, amountMinor, currency,
                BigDecimal.ONE, baseAmountMinor, null, occurredOn, status, description, merchant, null, null,
                null);
        return transactions.save(tx).getId();
    }
}
