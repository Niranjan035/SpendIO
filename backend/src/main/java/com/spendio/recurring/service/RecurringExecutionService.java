package com.spendio.recurring.service;

import com.spendio.recurring.domain.RecurringRun;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.recurring.dto.response.RecurringRunResponse;
import com.spendio.recurring.mapper.RecurringMapper;
import com.spendio.recurring.repository.RecurringRunRepository;
import com.spendio.recurring.repository.RecurringTransactionRepository;
import com.spendio.sharedkernel.domain.enums.RecurringRunStatus;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.RecurringRunFailedEvent;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.dto.request.CreateTransferRequest;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.dto.response.TransferResponse;
import com.spendio.transactions.service.TransactionService;
import com.spendio.transactions.service.TransferService;
import com.spendio.transactions.support.DedupeKeyFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Occurrence execution (ARCHITECTURE.md J.5/J.8): the scheduler claims due rules, then
 * posts each occurrence in its own REQUIRES_NEW transaction, and run-now/resume
 * catch-up reuse the same {@link #process} primitive. Correctness rests on three
 * layers, in this order:
 *
 * <ol>
 *   <li>the rule row lock - every attempt serialises on it, so schedule advancement can
 *       never interleave and a stale {@code next_run_date} is always re-checked;</li>
 *   <li>{@code uk_run (rule, scheduled_for)} - the hard "once per occurrence" guarantee
 *       across nodes; a lost insert race surfaces as an exception that resolves to
 *       {@code DUPLICATE};</li>
 *   <li>the run row itself - a FAILED row marks a retryable attempt, so a re-claim of
 *       the same date resumes the retry budget instead of double posting.</li>
 * </ol>
 *
 * <p>Failures never escape the scheduler: the run row and the rule's retry budget are
 * recorded in a fresh transaction ({@link #recordFailure}) after the posting attempt
 * rolled back. Run-now surfaces them instead, after the same bookkeeping.</p>
 */
@Service
public class RecurringExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExecutionService.class);

    /** Missed-occurrence walk guard (the schedule itself is finite via end_date). */
    private static final int MAX_MISSED = 10_000;

    private final RecurringTransactionRepository rules;
    private final RecurringRunRepository runs;
    private final RecurrenceScheduleCalculator calculator;
    private final RecurringMapper mapper;
    private final TransactionService transactionService;
    private final TransferService transferService;
    private final TransactionTemplate template;
    private final ApplicationEventPublisher events;
    private final int batchSize;
    private final int catchUpLimit;

    public RecurringExecutionService(RecurringTransactionRepository rules, RecurringRunRepository runs,
            RecurrenceScheduleCalculator calculator, RecurringMapper mapper, TransactionService transactionService,
            TransferService transferService, PlatformTransactionManager transactionManager,
            ApplicationEventPublisher events,
            @Value("${spendio.recurring.batch-size:100}") int batchSize,
            @Value("${spendio.recurring.catch-up-limit:3}") int catchUpLimit) {
        this.rules = rules;
        this.runs = runs;
        this.calculator = calculator;
        this.mapper = mapper;
        this.transactionService = transactionService;
        this.transferService = transferService;
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.events = events;
        this.batchSize = batchSize;
        this.catchUpLimit = catchUpLimit;
    }

    /** One scheduler tick: claim the due batch, then post each occurrence on its own. */
    public ExecutionSummary executeDue() {
        LocalDate today = LocalDate.now();
        List<DueItem> due = template.execute(status -> rules.claimDue(today, batchSize).stream()
                .map(rule -> new DueItem(rule.getId(), rule.getNextRunDate()))
                .toList());
        int posted = 0;
        int skipped = 0;
        int failed = 0;
        for (DueItem item : due) {
            switch (process(item.id(), item.date(), true, true, null).outcome()) {
                case SUCCESS -> posted++;
                case FAILED -> failed++;
                case SKIPPED, DUPLICATE -> skipped++;
            }
        }
        return new ExecutionSummary(due.size(), posted, skipped, failed);
    }

    /**
     * Run-now (J.8): posts the pending occurrence at {@code next_run_date} regardless of
     * how far away it is. A supplied {@code Idempotency-Key} first replays through the
     * ledger row that carries it, so a retried HTTP request returns the original run.
     */
    public RecurringRunResponse runNow(Long userId, Long ruleId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String token = DedupeKeyFactory.idempotencyKey(userId, idempotencyKey);
            Optional<Long> replayed = transactionService.recurringRunForDedupeKey(userId, token);
            if (replayed.isPresent()) {
                RecurringRun run = runs.findById(replayed.get()).orElse(null);
                if (run != null) {
                    return mapper.toRunResponse(run);
                }
            }
        }
        LocalDate date = rules.findByIdAndDeletedAtIsNull(ruleId)
                .map(RecurringTransaction::getNextRunDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECURRING_NOT_FOUND, "Recurring transaction not found")
                        .withProperty("id", ruleId));
        ProcessResult result = process(ruleId, date, true, false, idempotencyKey);
        return switch (result.outcome()) {
            case SUCCESS, DUPLICATE -> mapper.toRunResponse(result.run());
            case SKIPPED -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("status", "the rule is no longer active");
            case FAILED -> throw (RuntimeException) result.error();
        };
    }

    /**
     * Resume catch-up (J.8): the plan transaction flips the rule back to ACTIVE and
     * records every occurrence missed while paused (dropping all but the last
     * {@code catch-up-limit} as {@code SKIPPED_PAUSED} rows), then each kept date is
     * generated in its own transaction and the schedule re-anchors past today - so no
     * missed date can ever be claimed again afterwards.
     *
     * @return the catch-up tallies, or {@code null} when the rule was not resumable
     *         (already active, or deleted by a concurrent request)
     */
    public CatchUpSummary resumeCatchUp(Long ruleId) {
        CatchUpPlan plan = template.execute(status -> buildPlan(ruleId));
        if (plan == null) {
            return null;
        }
        int posted = 0;
        int failed = 0;
        LocalDate lastPosted = null;
        for (LocalDate date : plan.generate()) {
            ProcessResult result = process(ruleId, date, true, false, null);
            if (result.outcome() == ProcessOutcome.SUCCESS) {
                posted++;
                lastPosted = result.run().getScheduledFor();
            } else if (result.outcome() == ProcessOutcome.FAILED) {
                failed++;
            }
        }
        finalizeResume(ruleId, posted, lastPosted);
        return new CatchUpSummary(plan.missed(), plan.generate().size(), plan.dropped(), posted, failed);
    }

    /**
     * One occurrence attempt. {@code verifySchedule} (scheduler only) additionally
     * requires the claimed date to still be the rule's {@code next_run_date}, so an
     * edit between claim and post skips instead of posting a stale date.
     */
    private ProcessResult process(Long ruleId, LocalDate date, boolean advance, boolean verifySchedule,
            String idempotencyKey) {
        boolean[] rowAlreadyPresent = {false};
        try {
            return template.execute(status -> attempt(ruleId, date, advance, verifySchedule, idempotencyKey,
                    rowAlreadyPresent));
        } catch (RuntimeException failure) {
            boolean wonByOtherNode = !rowAlreadyPresent[0]
                    && runs.existsByRecurringTransactionIdAndScheduledFor(ruleId, date);
            if (wonByOtherNode) {
                log.debug("recurring rule {} date {} lost the uk_run race", ruleId, date);
                return new ProcessResult(ProcessOutcome.DUPLICATE,
                        runs.findByRecurringTransactionIdAndScheduledFor(ruleId, date).orElse(null), null);
            }
            return recordFailure(ruleId, date, failure);
        }
    }

    private ProcessResult attempt(Long ruleId, LocalDate date, boolean advance, boolean verifySchedule,
            String idempotencyKey, boolean[] rowAlreadyPresent) {
        RecurringTransaction rule = rules.findByIdForUpdate(ruleId).orElse(null);
        if (rule == null || rule.getStatus() != RecurringStatus.ACTIVE) {
            log.debug("recurring rule {} not executable on {} (missing or {})", ruleId, date,
                    rule == null ? "deleted" : rule.getStatus());
            return new ProcessResult(ProcessOutcome.SKIPPED, null, null);
        }
        if (verifySchedule && !date.equals(rule.getNextRunDate())) {
            log.debug("recurring rule {} moved on since claim (was {})", ruleId, date);
            return new ProcessResult(ProcessOutcome.SKIPPED, null, null);
        }

        Optional<RecurringRun> found = runs.findForUpdate(ruleId, date);
        RecurringRun run;
        if (found.isPresent()) {
            rowAlreadyPresent[0] = true;
            RecurringRun existing = found.get();
            if (existing.getStatus() != RecurringRunStatus.FAILED) {
                // Another node (or an earlier catch-up) already finished this date.
                if (advance && date.equals(rule.getNextRunDate())) {
                    rule.advanceTo(calculator.next(rule, date), date);
                }
                return new ProcessResult(ProcessOutcome.DUPLICATE, existing, null);
            }
            existing.countAttempt();
            run = existing;
        } else {
            run = RecurringRun.claim(ruleId, rule.getUserId(), date);
            runs.saveAndFlush(run);
        }

        Long transactionId = generateTransaction(rule, run.getId(), date, idempotencyKey);
        run.markSuccess(transactionId, Instant.now());
        runs.save(run);
        rule.clearFailure();
        if (advance && date.equals(rule.getNextRunDate())) {
            rule.advanceTo(calculator.next(rule, date), date);
        }
        return new ProcessResult(ProcessOutcome.SUCCESS, run, null);
    }

    private Long generateTransaction(RecurringTransaction rule, Long runId, LocalDate date, String idempotencyKey) {
        if (rule.getType() == TransactionType.TRANSFER) {
            CreateTransferRequest request = new CreateTransferRequest(
                    rule.getAccount().getId(),
                    rule.getToAccount().getId(),
                    rule.getAmountMinor(),
                    rule.getCurrencyCode(),
                    null,
                    date,
                    rule.getDescription(),
                    rule.getNotes());
            TransferResponse response = transferService.createForRecurring(rule.getUserId(), request,
                    rule.getId(), runId, idempotencyKey);
            return response.fromTransaction().id();
        }
        CreateTransactionRequest request = new CreateTransactionRequest(
                rule.getAccount().getId(),
                rule.getCategory() == null ? null : rule.getCategory().getId(),
                rule.getType(),
                null,
                rule.getAmountMinor(),
                rule.getCurrencyCode(),
                null,
                date,
                rule.getDescription(),
                null,
                null,
                rule.getNotes(),
                rule.isAutoPost() ? TransactionStatus.POSTED : TransactionStatus.PENDING,
                null);
        TransactionResponse response = transactionService.createForRecurring(rule.getUserId(), request,
                rule.getId(), runId, idempotencyKey);
        return response.id();
    }

    /**
     * Bookkeeping after a posting attempt rolled back: a fresh transaction re-locks the
     * rule, marks the run row FAILED (creating it when the claim insert itself was what
     * failed) and spends one step of the rule's retry budget - which flips the rule to
     * FAILED once {@code max_retries} is reached. A finished run row is never corrupted.
     */
    private ProcessResult recordFailure(Long ruleId, LocalDate date, RuntimeException failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        try {
            template.execute(status -> {
                rules.findByIdForUpdate(ruleId).ifPresent(rule -> {
                    RecurringRun run;
                    Optional<RecurringRun> existing = runs.findForUpdate(ruleId, date);
                    if (existing.isPresent()) {
                        run = existing.get();
                        if (run.getStatus() != RecurringRunStatus.FAILED) {
                            log.warn("recurring rule {} date {} failed but its run is {}", ruleId, date,
                                    run.getStatus());
                            return;
                        }
                        run.countAttempt();
                    } else {
                        run = RecurringRun.claim(ruleId, rule.getUserId(), date);
                    }
                    run.markFailed(message, Instant.now());
                    runs.save(run);
                    rule.markFailure(message);
                    events.publishEvent(new RecurringRunFailedEvent(
                            rule.getUserId(),
                            rule.getId(),
                            displayName(rule),
                            date,
                            message,
                            Instant.now(),
                            RecurringRunFailedEvent.dedupeKey(rule.getId(), date)));
                });
                return null;
            });
        } catch (RuntimeException recordingFailure) {
            log.warn("could not record recurring failure for rule {} on {}: {}", ruleId, date,
                    recordingFailure.getMessage());
        }
        log.warn("recurring rule {} failed to post {}: {}", ruleId, date, message);
        return new ProcessResult(ProcessOutcome.FAILED, null, failure);
    }

    /**
     * Plan transaction (J.8 step 1): only a PAUSED or FAILED rule resumes. Occurrences
     * missed while stopped are everything in {@code [next_run_date, today)} - the last
     * {@code catch-up-limit} are regenerated, the rest become {@code SKIPPED_PAUSED}
     * rows so history explains the gap. {@code next_run_date} itself is re-anchored in
     * {@link #finalizeResume}, after generation, because a mid-loop scheduler claim must
     * still see a coherent schedule.
     */
    private CatchUpPlan buildPlan(Long ruleId) {
        RecurringTransaction rule = rules.findByIdForUpdate(ruleId).orElse(null);
        if (rule == null
                || (rule.getStatus() != RecurringStatus.PAUSED && rule.getStatus() != RecurringStatus.FAILED)) {
            return null;
        }
        LocalDate today = LocalDate.now();
        List<LocalDate> missed = new ArrayList<>();
        LocalDate cursor = rule.getNextRunDate();
        while (cursor != null && cursor.isBefore(today) && missed.size() < MAX_MISSED) {
            missed.add(cursor);
            cursor = calculator.next(rule, cursor);
        }
        int keep = Math.min(catchUpLimit, missed.size());
        List<LocalDate> generate = List.copyOf(missed.subList(missed.size() - keep, missed.size()));
        List<LocalDate> dropped = List.copyOf(missed.subList(0, missed.size() - keep));
        skipDropped(rule, dropped);
        rule.resume();
        return new CatchUpPlan(missed.size(), generate, dropped.size());
    }

    private void skipDropped(RecurringTransaction rule, List<LocalDate> dropped) {
        if (dropped.isEmpty()) {
            return;
        }
        Set<LocalDate> alreadyRun = new HashSet<>();
        for (RecurringRun run : runs.findByRecurringTransactionIdAndScheduledForIn(rule.getId(), dropped)) {
            alreadyRun.add(run.getScheduledFor());
        }
        for (LocalDate date : dropped) {
            if (!alreadyRun.contains(date)) {
                runs.save(RecurringRun.skippedPaused(rule.getId(), rule.getUserId(), date));
            }
        }
    }

    /**
     * Plan transaction (J.8 step 3): the schedule resumes at the first occurrence on or
     * after {@code max(today, next_run_date)} - never before today's own occurrence, and
     * never at an already-executed date - or completes when none remains.
     */
    private void finalizeResume(Long ruleId, int posted, LocalDate lastPosted) {
        template.execute(status -> {
            rules.findByIdForUpdate(ruleId).ifPresent(rule -> {
                LocalDate today = LocalDate.now();
                LocalDate floor = today.isAfter(rule.getNextRunDate()) ? today : rule.getNextRunDate();
                LocalDate next = calculator.firstOnOrAfter(rule, floor);
                if (next == null) {
                    rule.markCompleted();
                } else {
                    rule.setNextRunDate(next);
                }
                if (posted > 0) {
                    rule.recordGenerated(posted, lastPosted);
                }
            });
            return null;
        });
    }

    /** Outcome of a single {@link #process} call. */
    enum ProcessOutcome {
        SUCCESS, DUPLICATE, SKIPPED, FAILED
    }

    record ProcessResult(ProcessOutcome outcome, RecurringRun run, Throwable error) {
    }

    /** Human name for a rule in notifications - the description, or a fallback. */
    private static String displayName(RecurringTransaction rule) {
        String description = rule.getDescription();
        return description == null || description.isBlank()
                ? "Recurring transaction #" + rule.getId()
                : description;
    }

    /** Due id plus the claimed {@code next_run_date} - scalars, safe detached. */
    private record DueItem(Long id, LocalDate date) {
    }

    /** Occurrences kept by resume, those dropped past the limit, and the missed total. */
    private record CatchUpPlan(int missed, List<LocalDate> generate, int dropped) {
    }

    /** Scheduler tick tallies (job detail line and tests). */
    public record ExecutionSummary(int claimed, int posted, int skipped, int failed) {
    }

    /** Resume catch-up tallies. */
    public record CatchUpSummary(int missed, int generated, int skipped, int posted, int failed) {
    }
}
