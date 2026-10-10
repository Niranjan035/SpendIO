package com.spendio.goals.service;

import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.goals.domain.Goal;
import com.spendio.goals.domain.GoalContribution;
import com.spendio.goals.dto.request.CreateContributionRequest;
import com.spendio.goals.dto.response.GoalContributionResponse;
import com.spendio.goals.mapper.GoalMapper;
import com.spendio.goals.repository.GoalContributionRepository;
import com.spendio.goals.repository.GoalRepository;
import com.spendio.sharedkernel.domain.enums.GoalContributionMethod;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.transactions.dto.request.CreateTransferRequest;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.dto.response.TransferResponse;
import com.spendio.transactions.service.TransactionQueryService;
import com.spendio.transactions.service.TransferService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deposits and withdrawals (ARCHITECTURE.md D.11/K.6).
 *
 * <p>The goal's cached total moves through {@link GoalRepository#addCurrent}, a single
 * guarded UPDATE whose WHERE clause re-checks both the bounds ({@code 0 <= current +
 * delta <= target}) and the ACTIVE status - two concurrent deposits serialise in the
 * database, so no increment can be lost and the total can never leave
 * {@code [0, target]} (the DB CHECKs back this up). A zero-row result means the guard
 * rejected the write, and this service re-reads to raise the precise error.
 *
 * <p>{@code TRANSFER} contributions optionally move real money: primary account →
 * the goal's linked account (backwards for withdrawals), created through
 * {@link TransferService} so the ledger legs, FX freeze and dedupe keys behave exactly
 * like a hand-made transfer. A supplied {@code transactionId} instead links an
 * existing ledger row (unique: one row backs at most one contribution).
 */
@Service
public class GoalContributionService {

    private final GoalRepository goals;
    private final GoalContributionRepository contributions;
    private final AccountQueryService accountQuery;
    private final TransactionQueryService transactionQuery;
    private final TransferService transfers;
    private final GoalMapper mapper;
    private final GoalMilestoneNotifier notifier;

    public GoalContributionService(GoalRepository goals, GoalContributionRepository contributions,
            AccountQueryService accountQuery, TransactionQueryService transactionQuery, TransferService transfers,
            GoalMapper mapper, GoalMilestoneNotifier notifier) {
        this.goals = goals;
        this.contributions = contributions;
        this.accountQuery = accountQuery;
        this.transactionQuery = transactionQuery;
        this.transfers = transfers;
        this.mapper = mapper;
        this.notifier = notifier;
    }

    @Transactional
    public GoalContributionResponse add(Long userId, Long goalId, CreateContributionRequest request) {
        Goal goal = goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId).orElseThrow(() -> notFound(goalId));
        goal.requireAcceptsContributions();

        long amount = request.amountMinor();
        if (amount == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("amountMinor", "must be non-zero");
        }
        if (amount < 0 && (request.notes() == null || request.notes().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("notes", "required for withdrawals");
        }
        LocalDate contributedOn = request.contributedOn() != null ? request.contributedOn() : LocalDate.now();
        GoalContributionMethod method =
                request.method() != null ? request.method() : GoalContributionMethod.MANUAL;

        Long transactionId = request.transactionId();
        if (method == GoalContributionMethod.TRANSFER && transactionId == null) {
            transactionId = createTransfer(userId, goal, amount, contributedOn, request.notes());
        } else if (transactionId != null) {
            linkExisting(userId, transactionId);
        }

        int updated = goals.addCurrent(goalId, userId, amount, GoalStatus.ACTIVE);
        if (updated == 0) {
            throw guardRejected(goalId, userId, amount);
        }

        GoalContribution contribution = GoalContribution.create(
                userId, goalId, amount, goal.getCurrencyCode(), contributedOn, method, transactionId,
                request.notes());
        contributions.save(contribution);

        // addCurrent cleared the persistence context: reload for milestone/completion checks.
        Goal fresh = goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId).orElseThrow(() -> notFound(goalId));
        notifier.evaluate(fresh, fresh.getCurrentAmountMinor() - amount);
        goals.markCompletedIfReached(goalId, GoalStatus.ACTIVE, GoalStatus.COMPLETED, Instant.now());

        return mapper.toContributionResponse(contribution);
    }

    /**
     * Removes a contribution and its share of the total in one transaction. The ledger
     * rows a TRANSFER contribution created stay untouched - reversal is an explicit
     * ledger operation ({@code /transactions/.../reverse}), not a side effect here.
     * Removal runs through the same guarded decrement, so a completed goal must be
     * reopened first (its total may no longer cover the target).
     */
    @Transactional
    public void remove(Long userId, Long goalId, Long contributionId) {
        goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId).orElseThrow(() -> notFound(goalId));
        GoalContribution contribution = contributions.findByIdAndGoalIdAndUserId(contributionId, goalId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOAL_NOT_FOUND, "Goal not found")
                        .withProperty("contributionId", contributionId));

        int updated = goals.addCurrent(goalId, userId, -contribution.getAmountMinor(), GoalStatus.ACTIVE);
        if (updated == 0) {
            throw guardRejected(goalId, userId, -contribution.getAmountMinor());
        }
        contributions.delete(contribution);
    }

    /** Primary → linked account (positive) or linked → primary (negative), in goal currency. */
    private Long createTransfer(Long userId, Goal goal, long amount, LocalDate on, String notes) {
        Long savingsAccountId = goal.getLinkedAccountId();
        if (savingsAccountId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("linkedAccountId",
                            "goal has no linked account: link one, or contribute with method MANUAL");
        }
        AccountResponse primary = accountQuery.primaryAccount(userId);
        AccountResponse savings = accountQuery.detail(userId, savingsAccountId);

        boolean inflow = amount > 0;
        AccountResponse from = inflow ? primary : savings;
        AccountResponse to = inflow ? savings : primary;
        if (!goal.getCurrencyCode().equals(from.currencyCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("currencyCode",
                            "goal currency " + goal.getCurrencyCode()
                                    + " must match the source account currency " + from.currencyCode());
        }

        TransferResponse transfer = transfers.create(userId, new CreateTransferRequest(
                from.id(), to.id(), Math.abs(amount), from.currencyCode(), null, on,
                "Goal contribution: " + goal.getName(), notes));
        return transfer.fromTransaction() == null ? null : transfer.fromTransaction().id();
    }

    private void linkExisting(Long userId, Long transactionId) {
        TransactionResponse transaction = transactionQuery.detail(userId, transactionId);
        if (transaction.type() == com.spendio.sharedkernel.domain.enums.TransactionType.TRANSFER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("transactionId",
                            "transfer legs back TRANSFER contributions automatically; omit transactionId");
        }
        if (contributions.findByTransactionId(transactionId).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Transaction already linked to a contribution")
                    .withProperty("transactionId", transactionId);
        }
    }

    /** addCurrent returned 0: distinguish status problems from bound violations. */
    private BusinessException guardRejected(Long goalId, Long userId, long delta) {
        Goal fresh = goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId).orElseThrow(() -> notFound(goalId));
        if (fresh.getStatus() != GoalStatus.ACTIVE) {
            fresh.requireAcceptsContributions(); // always throws for a non-ACTIVE goal
        }
        long wouldBe = fresh.getCurrentAmountMinor() + delta;
        if (wouldBe > fresh.getTargetAmountMinor()) {
            return new BusinessException(ErrorCode.GOAL_CONTRIBUTION_EXCEEDS_TARGET,
                    "Contribution exceeds the goal target")
                    .withProperty("goalId", goalId)
                    .withProperty("currentAmountMinor", fresh.getCurrentAmountMinor())
                    .withProperty("targetAmountMinor", fresh.getTargetAmountMinor());
        }
        if (wouldBe < 0) {
            return new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("amountMinor", "withdrawal exceeds the amount already contributed");
        }
        return new BusinessException(ErrorCode.CONFLICT, "The goal was modified by another request")
                .withProperty("goalId", goalId);
    }

    private static BusinessException notFound(Long goalId) {
        return new BusinessException(ErrorCode.GOAL_NOT_FOUND, "Goal not found")
                .withProperty("id", goalId);
    }
}
