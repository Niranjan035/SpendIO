package com.spendio.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.goals.domain.Goal;
import com.spendio.goals.domain.GoalContribution;
import com.spendio.goals.dto.request.CreateContributionRequest;
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
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Deposits and withdrawals (ARCHITECTURE.md D.11): the guarded single-statement
 * increment (concurrent deposits serialize in the DB, never read-modify-write), the
 * exceeds-target rejection, withdrawal-with-notes, and the transfer-linked path that
 * moves real money through {@link TransferService} from the primary account into the
 * goal's linked account.
 */
@ExtendWith(MockitoExtension.class)
class GoalContributionServiceTest {

    private static final long USER = 1L;
    private static final long GOAL_ID = 9L;
    private static final long PRIMARY = 3L;
    private static final long SAVINGS = 5L;
    private static final long TRANSACTION_ID = 42L;

    @Mock
    private GoalRepository goals;
    @Mock
    private GoalContributionRepository contributions;
    @Mock
    private AccountQueryService accountQuery;
    @Mock
    private TransactionQueryService transactionQuery;
    @Mock
    private TransferService transfers;
    @Mock
    private GoalMapper mapper;
    @Mock
    private GoalMilestoneNotifier notifier;

    private GoalContributionService service;

    @BeforeEach
    void setUp() {
        service = new GoalContributionService(goals, contributions, accountQuery, transactionQuery, transfers,
                mapper, notifier);
    }

    @Test
    void addAppliesTheGuardedIncrementAndSavesTheContribution() {
        Goal goal = goal(10_000, 100_000, GoalStatus.ACTIVE, SAVINGS);
        owned(goal);
        when(goals.addCurrent(GOAL_ID, USER, 25_000, GoalStatus.ACTIVE)).thenReturn(1);
        when(goals.markCompletedIfReached(eq(GOAL_ID), eq(GoalStatus.ACTIVE), eq(GoalStatus.COMPLETED),
                any())).thenReturn(0);

        service.add(USER, GOAL_ID, new CreateContributionRequest(25_000L, null, null, null, null));

        verify(contributions).save(any(GoalContribution.class));
        // The cache is guarded in a single UPDATE; this service never writes it itself.
        verify(notifier).evaluate(eq(goal), anyLong());
    }

    @Test
    void rejectsADepositThatWouldExceedTheTarget() {
        Goal goal = goal(90_000, 100_000, GoalStatus.ACTIVE, null);
        owned(goal);
        when(goals.addCurrent(GOAL_ID, USER, 20_000, GoalStatus.ACTIVE)).thenReturn(0);

        assertThatThrownBy(() -> service.add(USER, GOAL_ID,
                new CreateContributionRequest(20_000L, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GOAL_CONTRIBUTION_EXCEEDS_TARGET));
        verify(contributions, never()).save(any());
    }

    @Test
    void rejectsAWithdrawalWithoutAReason() {
        Goal goal = goal(50_000, 100_000, GoalStatus.ACTIVE, null);
        owned(goal);

        assertThatThrownBy(() -> service.add(USER, GOAL_ID,
                new CreateContributionRequest(-5_000L, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(goals, never()).addCurrent(any(), any(), anyLong(), any());
    }

    @Test
    void rejectsContributionsToACompletedGoal() {
        Goal goal = goal(100_000, 100_000, GoalStatus.COMPLETED, null);
        owned(goal);

        assertThatThrownBy(() -> service.add(USER, GOAL_ID,
                new CreateContributionRequest(100L, null, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GOAL_ALREADY_COMPLETED));
    }

    @Test
    void transferLinkedContributionMovesMoneyFromPrimaryToSavingsAndLinksTheLeg() {
        Goal goal = goal(20_000, 100_000, GoalStatus.ACTIVE, SAVINGS);
        owned(goal);
        when(goals.addCurrent(GOAL_ID, USER, 50_000, GoalStatus.ACTIVE)).thenReturn(1);
        when(goals.markCompletedIfReached(any(), any(), any(), any())).thenReturn(0);
        when(accountQuery.primaryAccount(USER))
                .thenReturn(response(PRIMARY, "USD"));
        when(accountQuery.detail(USER, SAVINGS))
                .thenReturn(response(SAVINGS, "USD"));
        TransactionResponse fromLeg = tx(TRANSACTION_ID);
        TransactionResponse toLeg = tx(TRANSACTION_ID + 1);
        when(transfers.create(eq(USER), any(CreateTransferRequest.class)))
                .thenReturn(new TransferResponse("gid", fromLeg, toLeg));

        service.add(USER, GOAL_ID,
                new CreateContributionRequest(50_000L, null, GoalContributionMethod.TRANSFER, null, null));

        ArgumentCaptor<CreateTransferRequest> transfer =
                ArgumentCaptor.forClass(CreateTransferRequest.class);
        verify(transfers).create(eq(USER), transfer.capture());
        assertThat(transfer.getValue().fromAccountId()).isEqualTo(PRIMARY);
        assertThat(transfer.getValue().toAccountId()).isEqualTo(SAVINGS);
        assertThat(transfer.getValue().amountMinor()).isEqualTo(50_000);

        var saved = ArgumentCaptor.forClass(GoalContribution.class);
        verify(contributions).save(saved.capture());
        assertThat(saved.getValue().getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.getValue().getMethod()).isEqualTo(GoalContributionMethod.TRANSFER);
    }

    @Test
    void transferRequiresALinkedAccount() {
        Goal goal = goal(0, 100_000, GoalStatus.ACTIVE, null);
        owned(goal);

        assertThatThrownBy(() -> service.add(USER, GOAL_ID,
                new CreateContributionRequest(10_000L, null, GoalContributionMethod.TRANSFER, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(transfers, never()).create(any(), any());
    }

    @Test
    void removeDecrementsTheTotalAndDeletesTheContribution() {
        Goal goal = goal(60_000, 100_000, GoalStatus.ACTIVE, null);
        owned(goal);
        GoalContribution contribution = GoalContribution.create(USER, GOAL_ID, 10_000, "USD",
                LocalDate.now(), GoalContributionMethod.MANUAL, null, null);
        ReflectionTestUtils.setField(contribution, "id", 100L);
        when(contributions.findByIdAndGoalIdAndUserId(100L, GOAL_ID, USER))
                .thenReturn(Optional.of(contribution));
        when(goals.addCurrent(GOAL_ID, USER, -10_000, GoalStatus.ACTIVE)).thenReturn(1);

        service.remove(USER, GOAL_ID, 100L);

        verify(contributions).delete(contribution);
    }

    private void owned(Goal goal) {
        when(goals.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER)).thenReturn(Optional.of(goal));
    }

    private static Goal goal(long current, long target, GoalStatus status, Long linkedAccount) {
        Goal goal = Goal.create(USER, "Holiday", null, target, "USD", null, null, (short) 0,
                null, null, (short) 90);
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        ReflectionTestUtils.setField(goal, "currentAmountMinor", current);
        ReflectionTestUtils.setField(goal, "status", status);
        ReflectionTestUtils.setField(goal, "completedAt", status == GoalStatus.COMPLETED
                ? java.time.Instant.now() : null);
        if (linkedAccount != null) {
            ReflectionTestUtils.setField(goal, "linkedAccount",
                    REFERENCE(linkedAccount));
        }
        return goal;
    }

    private static com.spendio.accounts.domain.Account REFERENCE(Long id) {
        com.spendio.accounts.domain.Account account = mock(com.spendio.accounts.domain.Account.class);
        lenient().when(account.getId()).thenReturn(id);
        return account;
    }

    private static AccountResponse response(Long id, String currency) {
        return mock(AccountResponse.class, invocation -> {
            return switch (invocation.getMethod().getName()) {
                case "id" -> id;
                case "currencyCode" -> currency;
                default -> invocation.getMethod().getDefaultValue();
            };
        });
    }

    private static TransactionResponse tx(long id) {
        TransactionResponse transaction = mock(TransactionResponse.class);
        lenient().when(transaction.id()).thenReturn(id);
        return transaction;
    }
}