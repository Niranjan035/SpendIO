package com.spendio.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Goal lifecycle (ARCHITECTURE.md D.11): future-only target dates for ACTIVE goals,
 * the lowering-too-far guard, name uniqueness, linked-account resolution and the
 * explicit complete/reopen transitions.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final long USER = 1L;
    private static final long GOAL_ID = 9L;
    private static final long ACCOUNT_ID = 3L;

    @Mock
    private GoalRepository goals;
    @Mock
    private AccountQueryService accountQuery;
    @Mock
    private EntityManager entityManager;
    @Mock
    private GoalMapper mapper;
    @Mock
    private GoalMilestoneNotifier notifier;
    @Mock
    private Account accountEntity;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goals, accountQuery, entityManager, mapper, notifier);
        lenient().when(accountEntity.getId()).thenReturn(ACCOUNT_ID);
        lenient().when(entityManager.getReference(eq(Account.class), eq(ACCOUNT_ID)))
                .thenReturn(accountEntity);
    }

    @Test
    void createsWithAGoalInTheFutureAndAhundredPercentTarget() {
        when(goals.existsByUserIdAndNameAndDeletedAtIsNull(USER, "Holiday")).thenReturn(false);

        service.create(USER, new CreateGoalRequest(
                "Holiday", null, 1_000_000L, "USD", LocalDate.now().plusMonths(6),
                null, 1, null, "#FF0000", 90));

        var captor = org.mockito.ArgumentCaptor.forClass(Goal.class);
        verify(goals).save(captor.capture());
        Goal saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Holiday");
        assertThat(saved.getTargetAmountMinor()).isEqualTo(1_000_000);
        assertThat(saved.getMilestonePct()).isEqualTo((short) 90);
        assertThat(saved.getStatus()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    void rejectsATargetDateInThePast() {
        assertThatThrownBy(() -> service.create(USER, new CreateGoalRequest(
                "Holiday", null, 1_000_000L, "USD", LocalDate.now().minusDays(1),
                null, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GOAL_TARGET_DATE_IN_PAST));
        verify(goals, never()).save(any());
    }

    @Test
    void rejectsADuplicateName() {
        when(goals.existsByUserIdAndNameAndDeletedAtIsNull(USER, "Holiday")).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, new CreateGoalRequest(
                "Holiday", null, 1_000_000L, "USD", null, null, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    @Test
    void resolvesTheLinkedAccountThroughTheAccountsModule() {
        when(goals.existsByUserIdAndNameAndDeletedAtIsNull(USER, "Holiday")).thenReturn(false);
        lenient().when(accountQuery.detail(USER, ACCOUNT_ID)).thenReturn(accountResponse());

        service.create(USER, new CreateGoalRequest(
                "Holiday", null, 1_000_000L, "USD", null, ACCOUNT_ID, null, null, null, null));

        var captor = org.mockito.ArgumentCaptor.forClass(Goal.class);
        verify(goals).save(captor.capture());
        assertThat(captor.getValue().getLinkedAccountId()).isEqualTo(ACCOUNT_ID);
        verify(accountQuery).detail(USER, ACCOUNT_ID);
    }

    @Test
    void rejectsLoweringTheTargetBelowWhatIsAlreadySaved() {
        Goal goal = goal(90_000, 100_000, GoalStatus.ACTIVE);
        owned(goal);

        assertThatThrownBy(() -> service.update(USER, GOAL_ID,
                new UpdateGoalRequest(null, null, 80_000L, null, null, null, null,
                        null, null, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GOAL_CONTRIBUTION_EXCEEDS_TARGET));
    }

    @Test
    void completesExplicitlyAndReopens() {
        Goal goal = goal(60_000, 100_000, GoalStatus.ACTIVE);
        owned(goal);

        service.complete(USER, GOAL_ID);
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(goal.getCompletedAt()).isNotNull();
        verify(notifier).notifyCompleted(goal);

        service.reopen(USER, GOAL_ID);
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(goal.getCompletedAt()).isNull();
    }

    @Test
    void refusingToCompleteTwice() {
        Goal goal = goal(100_000, 100_000, GoalStatus.COMPLETED);
        owned(goal);

        assertThatThrownBy(() -> service.complete(USER, GOAL_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GOAL_ALREADY_COMPLETED));
    }

    @Test
    void statusCanPauseButNotCompleteThroughUpdate() {
        Goal goal = goal(50_000, 100_000, GoalStatus.ACTIVE);
        owned(goal);

        service.update(USER, GOAL_ID, new UpdateGoalRequest(
                null, null, null, null, null, null, null,
                null, null, null, null, GoalStatus.PAUSED, null));
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.PAUSED);

        assertThatThrownBy(() -> service.update(USER, GOAL_ID, new UpdateGoalRequest(
                null, null, null, null, null, null, null,
                null, null, null, null, GoalStatus.COMPLETED, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private void owned(Goal goal) {
        when(goals.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER)).thenReturn(Optional.of(goal));
    }

    private static Goal goal(long current, long target, GoalStatus status) {
        Goal goal = Goal.create(USER, "Holiday", null, target, "USD", null, null, (short) 0,
                null, null, (short) 90);
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        ReflectionTestUtils.setField(goal, "currentAmountMinor", current);
        ReflectionTestUtils.setField(goal, "status", status);
        if (status == GoalStatus.COMPLETED) {
            ReflectionTestUtils.setField(goal, "completedAt", java.time.Instant.now());
        }
        return goal;
    }

    private static AccountResponse accountResponse() {
        return org.mockito.Mockito.mock(AccountResponse.class, invocation -> {
            return "id".equals(invocation.getMethod().getName()) ? ACCOUNT_ID : null;
        });
    }
}