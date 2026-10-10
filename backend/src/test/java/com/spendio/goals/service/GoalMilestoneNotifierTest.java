package com.spendio.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.spendio.goals.domain.Goal;
import com.spendio.sharedkernel.events.GoalMilestoneEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Milestone events (ARCHITECTURE.md D.11/K.6): fires when an increment crosses the
 * configured threshold (or the target), and only then - re-evaluating after the
 * crossing is silent, so each milestone notifies exactly once.
 */
@ExtendWith(MockitoExtension.class)
class GoalMilestoneNotifierTest {

    private static final long USER = 1L;
    private static final long GOAL_ID = 9L;
    private static final long TARGET = 100_000;

    @Mock
    private ApplicationEventPublisher events;

    private GoalMilestoneNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new GoalMilestoneNotifier(events);
    }

    @Test
    void firesOncePerMilestoneEvenAcrossReevaluations() {
        Goal goal = goal(0, TARGET, (short) 90);

        notifier.evaluate(goal, 0);               // still under 90%
        notifier.evaluate(goal, 0);               // 89 999 -> no crossing
        setCurrent(goal, 90_000);
        notifier.evaluate(goal, 89_999);          // crosses 90%
        notifier.evaluate(goal, 90_000);          // noisy re-evaluation: silent
        setCurrent(goal, TARGET);
        notifier.evaluate(goal, 90_000);          // crosses 100%

        var events_ = ArgumentCaptor.forClass(GoalMilestoneEvent.class);
        verify(events, org.mockito.Mockito.times(2)).publishEvent(events_.capture());
        assertThat(events_.getAllValues()).extracting(GoalMilestoneEvent::milestonePct)
                .containsExactly((short) 90, (short) 100);
        assertThat(events_.getAllValues().get(0).dedupeKey())
                .isEqualTo("goal:" + GOAL_ID + ":milestone:90");
    }

    @Test
    void aWithdrawalBackBelowTheThresholdDoesNotReplayTheMilestoneEvent() {
        Goal goal = goal(90_000, TARGET, (short) 90);
        notifier.evaluate(goal, 90_000); // already past 90%

        setCurrent(goal, 80_000);
        notifier.evaluate(goal, 90_000); // dropped back, no new crossing of 90%

        verify(events, never()).publishEvent(anyEvent());
    }

    @Test
    void completedNotifiesOnlyWhenTheTargetIsActuallyReached() {
        Goal belowTarget = goal(50_000, TARGET, (short) 90);
        notifier.notifyCompleted(belowTarget);
        verify(events, never()).publishEvent(anyEvent());

        Goal reached = goal(TARGET, TARGET, (short) 90);
        notifier.notifyCompleted(reached);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(GoalMilestoneEvent.class, milestone ->
                assertThat(milestone.milestonePct()).isEqualTo((short) 100));
    }

    @Test
    void milestoneAmountsRoundUpWithIntegerMath() {
        assertThat(GoalMilestoneNotifier.ceilPct(100_000, (short) 90)).isEqualTo(90_000);
        assertThat(GoalMilestoneNotifier.ceilPct(100_000, (short) 25)).isEqualTo(25_000);
        assertThat(GoalMilestoneNotifier.ceilPct(999, (short) 100)).isEqualTo(999);
        assertThat(GoalMilestoneNotifier.ceilPct(10_000, (short) 1)).isEqualTo(100);
    }

    private static Goal goal(long current, long target, short milestonePct) {
        Goal goal = Goal.create(USER, "Holiday", null, target, "USD", null, null, (short) 0,
                null, null, milestonePct);
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        setCurrent(goal, current);
        return goal;
    }

    private static void setCurrent(Goal goal, long current) {
        ReflectionTestUtils.setField(goal, "currentAmountMinor", current);
    }

    private static GoalMilestoneEvent anyEvent() {
        return org.mockito.ArgumentMatchers.any(GoalMilestoneEvent.class);
    }
}