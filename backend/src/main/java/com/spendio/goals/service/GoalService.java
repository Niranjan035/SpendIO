package com.spendio.goals.service;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.goals.domain.Goal;
import com.spendio.goals.dto.request.CreateGoalRequest;
import com.spendio.goals.dto.request.UpdateGoalRequest;
import com.spendio.goals.dto.response.GoalResponse;
import com.spendio.goals.mapper.GoalMapper;
import com.spendio.goals.repository.GoalRepository;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Goal lifecycle (ARCHITECTURE.md D.11): create with a future target date, partial
 * edits guarded by {@code @Version}, soft delete that keeps contribution history, and
 * the explicit complete/reopen transitions. Completion is the only place
 * {@code completed_at} is written (DB CHECK keeps them consistent), and automatic
 * completion lives next to the increment that reaches the target in
 * {@link GoalContributionService}.
 */
@Service
public class GoalService {

    private final GoalRepository goals;
    private final AccountQueryService accountQuery;
    private final EntityManager entityManager;
    private final GoalMapper mapper;
    private final GoalMilestoneNotifier notifier;

    public GoalService(GoalRepository goals, AccountQueryService accountQuery, EntityManager entityManager,
            GoalMapper mapper, GoalMilestoneNotifier notifier) {
        this.goals = goals;
        this.accountQuery = accountQuery;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.notifier = notifier;
    }

    @Transactional
    public GoalResponse create(Long userId, CreateGoalRequest request) {
        String name = requireName(request.name());
        requireNameFree(userId, name, null);
        LocalDate targetDate = request.targetDate();
        if (targetDate != null && targetDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.GOAL_TARGET_DATE_IN_PAST, "Goal target date is in the past")
                    .withProperty("targetDate", targetDate);
        }
        Account linked = request.linkedAccountId() == null ? null : referenceAccount(userId, request.linkedAccountId());

        Goal goal = Goal.create(
                userId,
                name,
                request.description(),
                request.targetAmountMinor(),
                normalize(request.currencyCode()),
                targetDate,
                linked,
                request.priority() == null ? 0 : request.priority().shortValue(),
                request.icon(),
                request.color(),
                request.milestonePct() == null ? 90 : request.milestonePct().shortValue());
        goals.save(goal);
        return mapper.toResponse(goal);
    }

    @Transactional
    public GoalResponse update(Long userId, Long id, UpdateGoalRequest request) {
        Goal goal = goals.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound(id));
        if (request.version() != null && !request.version().equals(goal.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "The goal was modified by another request")
                    .withProperty("expectedVersion", request.version())
                    .withProperty("currentVersion", goal.getVersion());
        }

        if (request.name() != null) {
            String name = requireName(request.name());
            requireNameFree(userId, name, goal.getId());
            goal.setName(name);
        }
        if (request.description() != null) {
            goal.setDescription(request.description());
        }
        if (request.targetAmountMinor() != null) {
            if (request.targetAmountMinor() < goal.getCurrentAmountMinor()) {
                throw new BusinessException(ErrorCode.GOAL_CONTRIBUTION_EXCEEDS_TARGET,
                        "Contribution exceeds the goal target")
                        .withProperty("targetAmountMinor", request.targetAmountMinor())
                        .withProperty("currentAmountMinor", goal.getCurrentAmountMinor());
            }
            goal.setTargetAmountMinor(request.targetAmountMinor());
        }
        if (Boolean.TRUE.equals(request.clearTargetDate())) {
            goal.setTargetDate(null);
        } else if (request.targetDate() != null) {
            if (goal.getStatus() == GoalStatus.ACTIVE && request.targetDate().isBefore(LocalDate.now())) {
                throw new BusinessException(ErrorCode.GOAL_TARGET_DATE_IN_PAST, "Goal target date is in the past")
                        .withProperty("targetDate", request.targetDate());
            }
            goal.setTargetDate(request.targetDate());
        }
        if (Boolean.TRUE.equals(request.clearLinkedAccount())) {
            goal.setLinkedAccount(null);
        } else if (request.linkedAccountId() != null) {
            goal.setLinkedAccount(referenceAccount(userId, request.linkedAccountId()));
        }
        if (request.priority() != null) {
            goal.setPriority(request.priority().shortValue());
        }
        if (request.icon() != null) {
            goal.setIcon(request.icon());
        }
        if (request.color() != null) {
            goal.setColor(request.color());
        }
        if (request.milestonePct() != null) {
            goal.setMilestonePct(request.milestonePct().shortValue());
        }
        if (request.status() != null) {
            requireStatusChangeAllowed(goal, request.status());
            if (request.status() == GoalStatus.ACTIVE) {
                goal.reopen();
            } else {
                goal.setStatus(request.status());
            }
        }
        return mapper.toResponse(goal);
    }

    /** Soft delete: contribution history stays (K.6) and keeps feeding reconciliation. */
    @Transactional
    public void delete(Long userId, Long id) {
        goals.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound(id)).softDelete();
    }

    @Transactional
    public GoalResponse complete(Long userId, Long id) {
        Goal goal = goals.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound(id));
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.GOAL_ALREADY_COMPLETED, "Goal is already completed")
                    .withProperty("id", id);
        }
        goal.complete(Instant.now());
        notifier.notifyCompleted(goal);
        return mapper.toResponse(goal);
    }

    @Transactional
    public GoalResponse reopen(Long userId, Long id) {
        Goal goal = goals.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound(id));
        goal.reopen();
        return mapper.toResponse(goal);
    }

    private static void requireStatusChangeAllowed(Goal goal, GoalStatus requested) {
        if (requested == GoalStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("status", "use POST /goals/{id}/complete to complete a goal");
        }
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.GOAL_ALREADY_COMPLETED, "Goal is already completed")
                    .withProperty("id", goal.getId());
        }
    }

    private Account referenceAccount(Long userId, Long accountId) {
        AccountResponse account = accountQuery.detail(userId, accountId);
        return entityManager.getReference(Account.class, account.id());
    }

    private void requireNameFree(Long userId, String name, Long selfId) {
        boolean taken = selfId == null
                ? goals.existsByUserIdAndNameAndDeletedAtIsNull(userId, name)
                : goals.existsByUserIdAndNameAndDeletedAtIsNullAndIdNot(userId, name, selfId);
        if (taken) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A goal with that name already exists")
                    .withProperty("name", name);
        }
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("name", "must not be blank");
        }
        return trimmed;
    }

    private static String normalize(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static BusinessException notFound(Long id) {
        return new BusinessException(ErrorCode.GOAL_NOT_FOUND, "Goal not found")
                .withProperty("id", id);
    }
}
