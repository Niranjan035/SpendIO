package com.spendio.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.accounts.service.AccountService;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.repository.CategoryRepository;
import com.spendio.categories.service.CategoryService;
import com.spendio.recurring.domain.RecurringRun;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.recurring.dto.response.RecurringRunResponse;
import com.spendio.recurring.job.RecurringTransactionExecutor;
import com.spendio.recurring.repository.RecurringRunRepository;
import com.spendio.recurring.repository.RecurringTransactionRepository;
import com.spendio.recurring.service.RecurringExecutionService.CatchUpSummary;
import com.spendio.recurring.service.RecurringExecutionService.ExecutionSummary;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.RecurringRunStatus;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Occurrence execution end-to-end (ARCHITECTURE.md J.5/J.8). Deliberately
 * <strong>non-transactional</strong>: the execution path posts in REQUIRES_NEW
 * transactions that suspend any test transaction and therefore cannot see uncommitted
 * seed data. Each test wipes the shared schema first (fresh user per test) and runs the
 * scheduler/run-now/resume entry points exactly as production does.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
class RecurringExecutionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProfileService profileService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private RecurringExecutionService execution;
    @Autowired
    private RecurringTransactionExecutor executor;
    @Autowired
    private RecurringTransactionRepository rules;
    @Autowired
    private RecurringRunRepository runs;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private JobExecutionLogRepository jobLog;
    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long checkingId;
    private long savingsId;
    private long foodId;
    private long salaryId;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void seedFreshWorkspace() {
        wipe();
        userId = seedUser();
        checkingId = accountService.create(userId, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null, null,
                null, false, null, true)).getId();
        savingsId = accountService.create(userId, new CreateAccountRequest(
                "Savings", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null, null,
                null, false, null, true)).getId();
        foodId = categoryService.create(userId,
                new CreateCategoryRequest("Food", "EXPENSE", null, null, null, false)).getId();
        salaryId = categoryService.create(userId,
                new CreateCategoryRequest("Salary", "INCOME", null, null, null, false)).getId();
    }

    // --- scheduler -----------------------------------------------------------

    @Test
    void executeDuePostsEveryDueRuleAndAdvancesEachSchedule() {
        long expenseId = seedRule(r -> { }).getId();
        long pendingId = seedRule(r -> r.setAutoPost(false)).getId();
        long transferId = seedRule(r -> {
            r.setType(TransactionType.TRANSFER);
            r.setCategory(null);
            r.setToAccount(accounts.findById(savingsId).orElseThrow());
        }).getId();
        seedRule(r -> r.pause());
        seedRule(r -> {
            r.setStartDate(today.plusDays(7));
            r.setNextRunDate(today.plusDays(7));
        });

        ExecutionSummary summary = execution.executeDue();

        assertThat(summary.claimed()).isEqualTo(3);
        assertThat(summary.posted()).isEqualTo(3);
        assertThat(summary.skipped()).isZero();
        assertThat(summary.failed()).isZero();

        for (long ruleId : new long[] {expenseId, pendingId, transferId}) {
            RecurringRun run = onlyRunFor(ruleId);
            assertThat(run.getStatus()).isEqualTo(RecurringRunStatus.SUCCESS);
            assertThat(run.getScheduledFor()).isEqualTo(today);
            assertThat(run.getTransactionId()).isNotNull();
            assertThat(run.getExecutedAt()).isNotNull();
            assertThat(run.getAttempts()).isEqualTo((short) 1);

            RecurringTransaction rule = reload(ruleId);
            assertThat(rule.getStatus()).isEqualTo(RecurringStatus.ACTIVE);
            assertThat(rule.getNextRunDate()).isEqualTo(today.plusDays(1));
            assertThat(rule.getLastRunDate()).isEqualTo(today);
            assertThat(rule.getGeneratedCount()).isEqualTo(1);
        }

        assertThat(ledgerRows(expenseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM transactions WHERE user_id = ? AND recurring_transaction_id = ?",
                String.class, userId, expenseId)).isEqualTo("POSTED");
        assertThat(ledgerRows(pendingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM transactions WHERE user_id = ? AND recurring_transaction_id = ?",
                String.class, userId, pendingId)).isEqualTo("PENDING");
        assertThat(ledgerRows(transferId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT transfer_group_id) FROM transactions "
                        + "WHERE user_id = ? AND recurring_transaction_id = ?",
                Integer.class, userId, transferId)).isEqualTo(1);
    }

    @Test
    void executeDueCountsAnOccurrenceAnotherNodeAlreadyPostedAsSkipped() {
        RecurringTransaction rule = seedRule(r -> { });
        RecurringRun postedElsewhere = RecurringRun.claim(rule.getId(), userId, today);
        postedElsewhere.markSuccess(null, Instant.now());
        runs.save(postedElsewhere);

        ExecutionSummary summary = execution.executeDue();

        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.posted()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failed()).isZero();

        RecurringTransaction reloaded = reload(rule.getId());
        assertThat(reloaded.getNextRunDate()).isEqualTo(today.plusDays(1));
        assertThat(reloaded.getGeneratedCount()).isEqualTo(1);
        assertThat(ledgerRows(rule.getId())).isZero();
        assertThat(onlyRunFor(rule.getId()).getStatus()).isEqualTo(RecurringRunStatus.SUCCESS);
    }

    // --- run now -------------------------------------------------------------

    @Test
    void runNowPostsAFarFutureOccurrenceAndReplaysTheSameIdempotencyKey() {
        LocalDate futureStart = today.plusDays(7);
        RecurringTransaction rule = seedRule(r -> {
            r.setStartDate(futureStart);
            r.setNextRunDate(futureStart);
        });

        RecurringRunResponse first = execution.runNow(userId, rule.getId(), "run-key-1");

        assertThat(first.status()).isEqualTo(RecurringRunStatus.SUCCESS);
        assertThat(first.scheduledFor()).isEqualTo(futureStart);
        assertThat(first.transactionId()).isNotNull();
        assertThat(first.attempts()).isEqualTo((short) 1);

        RecurringRunResponse replay = execution.runNow(userId, rule.getId(), "run-key-1");

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(replay.status()).isEqualTo(RecurringRunStatus.SUCCESS);

        assertThat(runsFor(rule.getId())).hasSize(1);
        assertThat(ledgerRows(rule.getId())).isEqualTo(1);

        RecurringTransaction reloaded = reload(rule.getId());
        assertThat(reloaded.getNextRunDate()).isEqualTo(futureStart.plusDays(1));
        assertThat(reloaded.getGeneratedCount()).isEqualTo(1);
    }

    @Test
    void runNowOnAPausedRuleIsRejectedWithoutTouchingTheRunLog() {
        RecurringTransaction rule = seedRule(r -> r.pause());

        assertThatThrownBy(() -> execution.runNow(userId, rule.getId(), null))
                .isInstanceOfSatisfying(BusinessException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(failure.getFieldErrors())
                            .anyMatch(error -> error.field().equals("status"));
                });

        assertThat(runsFor(rule.getId())).isEmpty();
        assertThat(ledgerRows(rule.getId())).isZero();
    }

    // --- failure bookkeeping -------------------------------------------------

    @Test
    void failedPostingsRecordTheRunAndSpendTheRetryBudgetUntilTheRuleFails() {
        RecurringTransaction rule = seedRule(r -> {
            r.setCategory(categories.findById(salaryId).orElseThrow());
            r.setMaxRetries((short) 2);
        });

        ExecutionSummary first = execution.executeDue();
        assertThat(first.claimed()).isEqualTo(1);
        assertThat(first.failed()).isEqualTo(1);

        RecurringRun afterFirst = onlyRunFor(rule.getId());
        assertThat(afterFirst.getStatus()).isEqualTo(RecurringRunStatus.FAILED);
        assertThat(afterFirst.getAttempts()).isEqualTo((short) 1);
        assertThat(afterFirst.getErrorMessage()).isNotNull();
        assertThat(afterFirst.getTransactionId()).isNull();
        RecurringTransaction afterFirstRule = reload(rule.getId());
        assertThat(afterFirstRule.getStatus()).isEqualTo(RecurringStatus.ACTIVE);
        assertThat(afterFirstRule.getRetryCount()).isEqualTo((short) 1);
        assertThat(afterFirstRule.getLastError()).isNotNull();
        assertThat(afterFirstRule.getFailureReason()).isNull();

        ExecutionSummary second = execution.executeDue();
        assertThat(second.claimed()).isEqualTo(1);
        assertThat(second.failed()).isEqualTo(1);

        RecurringRun afterSecond = onlyRunFor(rule.getId());
        assertThat(afterSecond.getStatus()).isEqualTo(RecurringRunStatus.FAILED);
        assertThat(afterSecond.getAttempts()).isEqualTo((short) 2);
        RecurringTransaction afterSecondRule = reload(rule.getId());
        assertThat(afterSecondRule.getStatus()).isEqualTo(RecurringStatus.FAILED);
        assertThat(afterSecondRule.getRetryCount()).isEqualTo((short) 2);
        assertThat(afterSecondRule.getFailureReason()).isNotNull();

        ExecutionSummary third = execution.executeDue();
        assertThat(third.claimed()).isZero();
        assertThat(third.posted()).isZero();
        assertThat(third.failed()).isZero();
        assertThat(ledgerRows(rule.getId())).isZero();
    }

    // --- resume catch-up -----------------------------------------------------

    @Test
    void resumeCatchUpGeneratesOnlyTheBoundedWindowAndSkipsTheRest() {
        LocalDate missedStart = today.minusDays(10);
        RecurringTransaction rule = seedRule(r -> {
            r.setStartDate(missedStart);
            r.setNextRunDate(missedStart);
            r.pause();
        });

        CatchUpSummary summary = execution.resumeCatchUp(rule.getId());

        assertThat(summary).isNotNull();
        assertThat(summary.missed()).isEqualTo(10);
        assertThat(summary.generated()).isEqualTo(3);
        assertThat(summary.skipped()).isEqualTo(7);
        assertThat(summary.posted()).isEqualTo(3);
        assertThat(summary.failed()).isZero();

        RecurringTransaction reloaded = reload(rule.getId());
        assertThat(reloaded.getStatus()).isEqualTo(RecurringStatus.ACTIVE);
        assertThat(reloaded.getNextRunDate()).isEqualTo(today);
        assertThat(reloaded.getLastRunDate()).isEqualTo(today.minusDays(1));
        assertThat(reloaded.getGeneratedCount()).isEqualTo(3);
        assertThat(reloaded.getRetryCount()).isZero();
        assertThat(reloaded.getLastError()).isNull();

        List<RecurringRun> allRuns = runsFor(rule.getId());
        assertThat(allRuns).hasSize(10);
        assertThat(allRuns).filteredOn(run -> run.getStatus() == RecurringRunStatus.SUCCESS)
                .hasSize(3)
                .allSatisfy(run -> {
                    assertThat(run.getScheduledFor())
                            .isAfterOrEqualTo(today.minusDays(3))
                            .isBefore(today);
                    assertThat(run.getTransactionId()).isNotNull();
                });
        assertThat(allRuns).filteredOn(run -> run.getStatus() == RecurringRunStatus.SKIPPED_PAUSED)
                .hasSize(7)
                .allSatisfy(run -> assertThat(run.getScheduledFor()).isBefore(today.minusDays(3)));
        assertThat(ledgerRows(rule.getId())).isEqualTo(3);
    }

    @Test
    void resumeCatchUpIsANoopOnAnActiveRuleAndReactivatesAFailedRuleWithNothingMissed() {
        RecurringTransaction active = seedRule(r -> { });
        assertThat(execution.resumeCatchUp(active.getId())).isNull();
        assertThat(reload(active.getId()).getStatus()).isEqualTo(RecurringStatus.ACTIVE);

        RecurringTransaction failed = seedRule(r -> {
            r.setMaxRetries((short) 1);
            r.markFailure("boom");
        });
        assertThat(reload(failed.getId()).getStatus()).isEqualTo(RecurringStatus.FAILED);

        CatchUpSummary summary = execution.resumeCatchUp(failed.getId());

        assertThat(summary).isNotNull();
        assertThat(summary.missed()).isZero();
        assertThat(summary.generated()).isZero();
        assertThat(summary.skipped()).isZero();
        assertThat(summary.posted()).isZero();
        assertThat(summary.failed()).isZero();

        RecurringTransaction reloaded = reload(failed.getId());
        assertThat(reloaded.getStatus()).isEqualTo(RecurringStatus.ACTIVE);
        assertThat(reloaded.getRetryCount()).isZero();
        assertThat(reloaded.getFailureReason()).isNull();
        assertThat(runsFor(failed.getId())).isEmpty();
    }

    // --- concurrency ---------------------------------------------------------

    @Test
    void twoConcurrentSchedulerTicksStillPostTheOccurrenceExactlyOnce() throws Exception {
        RecurringTransaction rule = seedRule(r -> { });
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ExecutionSummary>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return execution.executeDue();
                }));
            }
            start.countDown();
            ExecutionSummary first = futures.get(0).get(30, TimeUnit.SECONDS);
            ExecutionSummary second = futures.get(1).get(30, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            assertThat(first.posted() + second.posted()).isEqualTo(1);
            assertThat(first.claimed() + second.claimed()).isBetween(1, 2);
            assertThat(first.failed() + second.failed()).isZero();
        } finally {
            pool.shutdownNow();
        }

        assertThat(runsFor(rule.getId())).hasSize(1);
        assertThat(onlyRunFor(rule.getId()).getStatus()).isEqualTo(RecurringRunStatus.SUCCESS);
        assertThat(ledgerRows(rule.getId())).isEqualTo(1);

        RecurringTransaction reloaded = reload(rule.getId());
        assertThat(reloaded.getNextRunDate()).isEqualTo(today.plusDays(1));
        assertThat(reloaded.getGeneratedCount()).isEqualTo(1);
    }

    // --- job entry point -----------------------------------------------------

    @Test
    void executorRunWritesAJobExecutionLogEntryWithTheTickTallies() {
        seedRule(r -> { });

        ExecutionSummary summary = executor.run();

        assertThat(summary.claimed()).isEqualTo(1);
        assertThat(summary.posted()).isEqualTo(1);

        JobExecutionLog entry = jobLog.findFirstByJobNameOrderByStartedAtDesc(
                RecurringTransactionExecutor.JOB_NAME).orElseThrow();
        assertThat(entry.getStatus()).isEqualTo("SUCCESS");
        assertThat(entry.getAffectedRows()).isEqualTo(1);
        assertThat(entry.getDetail()).contains("claimed=1", "posted=1");
        assertThat(entry.getFinishedAt()).isNotNull();
    }

    // --- helpers -------------------------------------------------------------

    private RecurringTransaction seedRule(Consumer<RecurringTransaction> customize) {
        RecurringTransaction rule = RecurringTransaction.create(
                userId,
                accounts.findById(checkingId).orElseThrow(),
                null,
                categories.findById(foodId).orElseThrow(),
                TransactionType.EXPENSE,
                TransactionDirection.OUTFLOW,
                1_500L,
                "USD",
                BigDecimal.ONE,
                1_500L,
                "Recurring occurrence",
                null,
                RecurrenceFrequency.DAILY,
                (short) 1,
                null,
                null,
                false,
                today,
                null,
                today,
                true,
                (short) 3,
                null);
        customize.accept(rule);
        return rules.save(rule);
    }

    private RecurringTransaction reload(long ruleId) {
        return rules.findById(ruleId).orElseThrow();
    }

    private RecurringRun onlyRunFor(long ruleId) {
        List<RecurringRun> found = runsFor(ruleId);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private List<RecurringRun> runsFor(long ruleId) {
        return runs.findByRecurringTransactionId(ruleId, Pageable.unpaged()).getContent();
    }

    private int ledgerRows(long ruleId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND recurring_transaction_id = ?",
                Integer.class, userId, ruleId);
    }

    private long seedUser() {
        UserResponse user = profileService.create(new CreateUserCommand(
                "execution-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Rita", "Runner",
                "UTC", "en-US", "USD"));
        return user.id();
    }

    /** FK-ordered wipe: this class commits, so every test starts from an empty schema. */
    private void wipe() {
        jdbc.update("UPDATE transactions "
                + "SET counterparty_transaction_id = NULL, reversal_of_transaction_id = NULL");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM recurring_transaction_runs");
        jdbc.update("DELETE FROM recurring_transactions");
        jdbc.update("DELETE FROM csv_import_rows");
        jdbc.update("DELETE FROM csv_imports");
        jdbc.update("UPDATE categories SET parent_id = NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM job_execution_log");
        jdbc.update("DELETE FROM users");
    }
}
