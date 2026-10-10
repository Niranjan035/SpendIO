package com.spendio.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.categories.domain.Category;
import com.spendio.categories.repository.CategoryRepository;
import com.spendio.goals.domain.Goal;
import com.spendio.goals.repository.GoalRepository;
import com.spendio.notifications.domain.Notification;
import com.spendio.notifications.repository.NotificationRepository;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.users.domain.User;
import com.spendio.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-tenant sweep (ARCHITECTURE.md Phase 15, D.16 tenancy): every user-scoped read
 * for one tenant must return nothing - and never another tenant's row - for a second
 * tenant, and must reject the foreign id when fetched by id. This exercises the real
 * repositories against MySQL so an accidentally unscoped query fails the build.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class OwnershipIsolationIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private BudgetRepository budgetRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private Long tenantA;
    private Long tenantB;

    private Account accountA;
    private Category categoryA;
    private Transaction transactionA;
    private Budget budgetA;
    private Goal goalA;
    private Notification notificationA;

    @BeforeEach
    void seedTwoTenants() {
        tenantA = userRepository.saveAndFlush(
                User.create("owner-a@example.com", "{bcrypt}", "UTC", "en-GB", "USD")).getId();
        tenantB = userRepository.saveAndFlush(
                User.create("owner-b@example.com", "{bcrypt}", "UTC", "en-GB", "USD")).getId();

        accountA = accountRepository.saveAndFlush(
                Account.create(tenantA, "A Cash", AccountType.CASH, "USD", 0L, LocalDate.now()));
        categoryA = categoryRepository.saveAndFlush(
                Category.create(tenantA, "A Food", Category.Type.EXPENSE, null, null, null, false));
        transactionA = transactionRepository.saveAndFlush(Transaction.create(
                tenantA, accountA, categoryA, TransactionType.EXPENSE, TransactionDirection.OUTFLOW,
                1_000L, "USD", BigDecimal.ONE, 1_000L, null, LocalDate.now(), TransactionStatus.POSTED,
                "A lunch", null, null, null, "owner-a-dedupe"));
        budgetA = budgetRepository.saveAndFlush(Budget.create(
                tenantA, "A Budget", categoryA, Budget.PeriodType.MONTHLY,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1),
                50_000L, "USD", 50_000L, false, (short) 80, true, false));
        goalA = goalRepository.saveAndFlush(Goal.create(
                tenantA, "A Holiday", null, 100_000L, "USD", null, null, (short) 1, null, null, (short) 100));
        notificationA = notificationRepository.saveAndFlush(Notification.create(
                tenantA, NotificationType.SYSTEM, NotificationSeverity.INFO, "A note", "body",
                null, null, null, Map.of(), "owner-a-note"));
    }

    @Test
    void ownerCanReadTheirOwnRows() {
        assertThat(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountA.getId(), tenantA)).isPresent();
        assertThat(categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(categoryA.getId(), tenantA)).isPresent();
        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(transactionA.getId(), tenantA)).isPresent();
        assertThat(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(budgetA.getId(), tenantA)).isPresent();
        assertThat(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalA.getId(), tenantA)).isPresent();
        assertThat(notificationRepository.findByIdAndUserIdAndDeletedAtIsNull(notificationA.getId(), tenantA)).isPresent();
    }

    @Test
    void foreignTenantCannotReadAnotherUsersRowsById() {
        assertThat(accountRepository.findByIdAndUserIdAndDeletedAtIsNull(accountA.getId(), tenantB)).isEmpty();
        assertThat(categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(categoryA.getId(), tenantB)).isEmpty();
        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(transactionA.getId(), tenantB)).isEmpty();
        assertThat(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(budgetA.getId(), tenantB)).isEmpty();
        assertThat(goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalA.getId(), tenantB)).isEmpty();
        assertThat(notificationRepository.findByIdAndUserIdAndDeletedAtIsNull(notificationA.getId(), tenantB)).isEmpty();
    }

    @Test
    void foreignTenantListingAndExistenceChecksStayEmpty() {
        assertThat(accountRepository.findByUserIdAndDeletedAtIsNull(tenantB)).isEmpty();
        assertThat(accountRepository.existsByUserIdAndNameAndDeletedAtIsNull(tenantB, "A Cash")).isFalse();
        assertThat(categoryRepository.findByUserIdAndDeletedAtIsNull(tenantB)).isEmpty();
        assertThat(transactionRepository.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(tenantB, "owner-a-dedupe"))
                .isFalse();
        assertThat(budgetRepository.existsByUserIdAndNameAndDeletedAtIsNull(tenantB, "A Budget")).isFalse();
        assertThat(goalRepository.existsByUserIdAndNameAndDeletedAtIsNull(tenantB, "A Holiday")).isFalse();
        assertThat(notificationRepository.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(tenantB, "owner-a-note"))
                .isFalse();
    }

    @Test
    void foreignTenantCannotTouchAnotherUsersLedgerTransfers() {
        assertThat(transactionRepository.findByUserIdAndTransferGroupIdAndDeletedAtIsNull(tenantB, "any-group"))
                .isEmpty();
        assertThat(transactionRepository.findByUserIdAndTransferGroupIdAndDeletedAtIsNull(tenantA, "any-group"))
                .isEmpty();
    }
}
