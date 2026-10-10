package com.spendio.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.recurring.dto.request.CreateRecurringRequest;
import com.spendio.recurring.dto.request.UpdateRecurringRequest;
import com.spendio.recurring.dto.response.CreateRecurringResponse;
import com.spendio.recurring.dto.response.RecurringRunResponse;
import com.spendio.recurring.mapper.RecurringMapper;
import com.spendio.recurring.repository.RecurringTransactionRepository;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.RecurringRunStatus;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.service.TransactionService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Rule lifecycle (ARCHITECTURE.md D.9/E.9): create-time validation and freezing,
 * the frequency/field matrix, PATCH re-validation with optimistic locking, the
 * pause/resume/cancel state machine and the run-now guards that precede execution.
 */
@ExtendWith(MockitoExtension.class)
class RecurringServiceTest {

    private static final long USER = 42L;
    private static final long ACCOUNT_ID = 5L;
    private static final long TO_ACCOUNT_ID = 6L;
    private static final long CATEGORY_ID = 7L;
    private static final long RULE_ID = 99L;
    private static final LocalDate PAST = LocalDate.now().minusDays(10);
    private static final LocalDate NEXT = LocalDate.now().plusDays(2);

    @Mock
    private RecurringTransactionRepository rules;
    @Mock
    private AccountQueryService accountQuery;
    @Mock
    private CategoryQueryService categoryQuery;
    @Mock
    private BaseCurrencyPort baseCurrency;
    @Mock
    private ObjectProvider<FxConversionPort> fxPort;
    @Mock
    private EntityManager entityManager;
    @Mock
    private RecurrenceScheduleCalculator calculator;
    @Mock
    private RecurringMapper mapper;
    @Mock
    private RecurringQueryService query;
    @Mock
    private RecurringExecutionService execution;
    @Mock
    private TransactionService transactionService;
    @Mock
    private Account accountEntity;
    @Mock
    private Account toAccountEntity;
    @Mock
    private Category categoryEntity;

    private RecurringService service;

    @BeforeEach
    void setUp() {
        service = new RecurringService(rules, accountQuery, categoryQuery, baseCurrency, fxPort, entityManager,
                calculator, mapper, query, execution, transactionService);
        lenient().when(accountEntity.getId()).thenReturn(ACCOUNT_ID);
        lenient().when(toAccountEntity.getId()).thenReturn(TO_ACCOUNT_ID);
        lenient().when(categoryEntity.getId()).thenReturn(CATEGORY_ID);
        lenient().when(entityManager.getReference(eq(Account.class), eq(ACCOUNT_ID))).thenReturn(accountEntity);
        lenient().when(entityManager.getReference(eq(Account.class), eq(TO_ACCOUNT_ID))).thenReturn(toAccountEntity);
        lenient().when(entityManager.getReference(eq(Category.class), eq(CATEGORY_ID))).thenReturn(categoryEntity);
        lenient().when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
    }

    // --- create ---------------------------------------------------------------

    @Test
    void createsAnExpenseRuleWithTheFirstOccurrenceAndAPreview() {
        stubActiveAccount(ACCOUNT_ID);
        stubCategory(CATEGORY_ID, Category.Type.EXPENSE);
        stubCalculator(NEXT, 5);

        CreateRecurringResponse response = service.create(USER, expenseRequest());

        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(rules).save(captor.capture());
        RecurringTransaction saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(saved.getDirection()).isEqualTo(TransactionDirection.OUTFLOW);
        assertThat(saved.getStatus()).isEqualTo(RecurringStatus.ACTIVE);
        assertThat(saved.getNextRunDate()).isEqualTo(NEXT);
        assertThat(saved.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(saved.getBaseAmountMinor()).isEqualTo(1_500L);
        assertThat(saved.isAutoPost()).isTrue();
        assertThat(saved.getMaxRetries()).isEqualTo((short) 3);
        assertThat(response.occurrences()).hasSize(5);
        verify(calculator).firstOnOrAfter(any(), eq(LocalDate.now()));
    }

    @Test
    void createsAnIncomeRuleWithInflowDirection() {
        stubActiveAccount(ACCOUNT_ID);
        stubCategory(CATEGORY_ID, Category.Type.INCOME);
        stubCalculator(NEXT, 5);

        service.create(USER, expenseRequestBuilder().type(TransactionType.INCOME).build());

        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(rules).save(captor.capture());
        assertThat(captor.getValue().getDirection()).isEqualTo(TransactionDirection.INFLOW);
    }

    @Test
    void rejectsCurrencyThatDiffersFromTheAccount() {
        stubActiveAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> service.create(USER,
                expenseRequestBuilder().currencyCode("EUR").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void rejectsAnArchivedAccount() {
        when(accountQuery.detail(USER, ACCOUNT_ID))
                .thenReturn(accountResponse(ACCOUNT_ID, "USD", AccountStatus.ARCHIVED));

        assertThatThrownBy(() -> service.create(USER, expenseRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_ARCHIVED));
    }

    @Test
    void expenseRulesRequireACategory() {
        stubActiveAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> service.create(USER,
                expenseRequestBuilder().categoryId(null).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getFieldErrors())
                        .extracting("field").contains("categoryId"));
    }

    @Test
    void transferRulesCarryATargetAccountAndNoCategory() {
        stubActiveAccount(ACCOUNT_ID);
        stubActiveAccount(TO_ACCOUNT_ID);
        stubCalculator(NEXT, 5);

        service.create(USER, expenseRequestBuilder()
                .type(TransactionType.TRANSFER)
                .toAccountId(TO_ACCOUNT_ID)
                .categoryId(null)
                .build());

        ArgumentCaptor<RecurringTransaction> captor = ArgumentCaptor.forClass(RecurringTransaction.class);
        verify(rules).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(captor.getValue().getToAccount()).isSameAs(toAccountEntity);
        assertThat(captor.getValue().getCategory()).isNull();
    }

    @Test
    void transferRulesRejectACategory() {
        stubActiveAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> service.create(USER, expenseRequestBuilder()
                .type(TransactionType.TRANSFER).toAccountId(TO_ACCOUNT_ID).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("categoryId");
                });
    }

    @Test
    void transferRulesRejectTheSameAccountOnBothSides() {
        stubActiveAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> service.create(USER, expenseRequestBuilder()
                .type(TransactionType.TRANSFER).toAccountId(ACCOUNT_ID).categoryId(null).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getFieldErrors())
                        .extracting("field").contains("toAccountId"));
    }

    @Test
    void expenseRulesRejectATransferTargetAccount() {
        stubActiveAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> service.create(USER,
                expenseRequestBuilder().toAccountId(TO_ACCOUNT_ID).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getFieldErrors())
                        .extracting("field").contains("toAccountId"));
    }

    @Test
    void rejectsACategoryOfTheWrongType() {
        stubActiveAccount(ACCOUNT_ID);
        stubCategory(CATEGORY_ID, Category.Type.INCOME);

        assertThatThrownBy(() -> service.create(USER, expenseRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void rejectsAScheduleThatProducesNoOccurrence() {
        stubActiveAccount(ACCOUNT_ID);
        stubCategory(CATEGORY_ID, Category.Type.EXPENSE);
        when(calculator.firstOnOrAfter(any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.create(USER, expenseRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_INVALID_SCHEDULE));
    }

    // --- schedule matrix ------------------------------------------------------

    @Test
    void frequencyFieldMatrixIsEnforced() {
        LocalDate start = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> RecurringService.validateSchedule(RecurrenceFrequency.MONTHLY,
                null, 1, null, start, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_FREQUENCY_FIELD_MISMATCH));

        assertThatThrownBy(() -> RecurringService.validateSchedule(RecurrenceFrequency.WEEKLY,
                15, null, null, start, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_FREQUENCY_FIELD_MISMATCH));

        assertThatThrownBy(() -> RecurringService.validateSchedule(RecurrenceFrequency.DAILY,
                null, 1, null, start, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_FREQUENCY_FIELD_MISMATCH));

        assertThatThrownBy(() -> RecurringService.validateSchedule(RecurrenceFrequency.CUSTOM,
                null, null, null, start, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_FREQUENCY_FIELD_MISMATCH));

        assertThatThrownBy(() -> RecurringService.validateSchedule(RecurrenceFrequency.DAILY,
                null, null, null, start, start.minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.RECURRING_INVALID_SCHEDULE);
                    assertThat(be.getProperties()).containsEntry("endDate", start.minusDays(1));
                });

        RecurringService.validateSchedule(RecurrenceFrequency.MONTHLY, 31, null, null, start, null);
        RecurringService.validateSchedule(RecurrenceFrequency.WEEKLY, null, 5, null, start, start);
        RecurringService.validateSchedule(RecurrenceFrequency.CUSTOM, null, null, start, start, null);
    }

    // --- update ---------------------------------------------------------------

    @Test
    void updateRejectsAStaleVersion() {
        RecurringTransaction rule = rule(RecurringStatus.ACTIVE);
        when(rules.findOwnedForUpdate(USER, RULE_ID)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.update(USER, RULE_ID, new UpdateRecurringRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, 7)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(be.getProperties()).containsEntry("currentVersion", rule.getVersion());
                });
        assertThat(rule.getAmountMinor()).isEqualTo(1_500L);
    }

    @Test
    void updateOfAnUnknownRuleIsNotFound() {
        when(rules.findOwnedForUpdate(USER, RULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, RULE_ID, new UpdateRecurringRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_NOT_FOUND));
    }

    @Test
    void updateReFreezesTheAmountAndResetsTheRetryBudget() {
        RecurringTransaction rule = rule(RecurringStatus.ACTIVE);
        rule.markFailure("gateway timed out");
        when(rules.findOwnedForUpdate(USER, RULE_ID)).thenReturn(Optional.of(rule));
        stubActiveAccount(ACCOUNT_ID);
        stubCategory(CATEGORY_ID, Category.Type.EXPENSE);
        when(calculator.firstOnOrAfter(any(), any())).thenReturn(NEXT);

        service.update(USER, RULE_ID, new UpdateRecurringRequest(
                null, null, null, null, 2_500L, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null));

        assertThat(rule.getAmountMinor()).isEqualTo(2_500L);
        assertThat(rule.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(rule.getBaseAmountMinor()).isEqualTo(2_500L);
        assertThat(rule.getNextRunDate()).isEqualTo(NEXT);
        assertThat(rule.getRetryCount()).isZero();
        assertThat(rule.getLastError()).isNull();
        assertThat(rule.getFailureReason()).isNull();
    }

    // --- lifecycle ------------------------------------------------------------

    @Test
    void pauseAndCancelFollowTheStateMachine() {
        RecurringTransaction active = rule(RecurringStatus.ACTIVE);
        when(rules.findOwnedForUpdate(USER, RULE_ID)).thenReturn(Optional.of(active));
        service.pause(USER, RULE_ID);
        assertThat(active.getStatus()).isEqualTo(RecurringStatus.PAUSED);

        RecurringTransaction paused = rule(RecurringStatus.PAUSED);
        when(rules.findOwnedForUpdate(USER, RULE_ID)).thenReturn(Optional.of(paused));
        service.cancel(USER, RULE_ID);
        assertThat(paused.getStatus()).isEqualTo(RecurringStatus.CANCELLED);
    }

    @Test
    void pausingAPausedRuleConflicts() {
        when(rules.findOwnedForUpdate(USER, RULE_ID))
                .thenReturn(Optional.of(rule(RecurringStatus.PAUSED)));

        assertThatThrownBy(() -> service.pause(USER, RULE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_ALREADY_PAUSED));
    }

    @Test
    void terminalRulesCannotBePausedOrCancelled() {
        when(rules.findOwnedForUpdate(USER, RULE_ID))
                .thenReturn(Optional.of(rule(RecurringStatus.CANCELLED)));

        assertThatThrownBy(() -> service.pause(USER, RULE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("status");
                });

        RecurringTransaction completed = rule(RecurringStatus.ACTIVE);
        completed.markCompleted();
        when(rules.findOwnedForUpdate(USER, RULE_ID)).thenReturn(Optional.of(completed));
        assertThatThrownBy(() -> service.cancel(USER, RULE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void resumeOnlyAcceptsPausedOrFailedRulesAndRunsTheCatchUp() {
        when(rules.findByIdAndUserIdAndDeletedAtIsNull(RULE_ID, USER))
                .thenReturn(Optional.of(rule(RecurringStatus.ACTIVE)));

        assertThatThrownBy(() -> service.resume(USER, RULE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(execution, never()).resumeCatchUp(any());

        when(rules.findByIdAndUserIdAndDeletedAtIsNull(RULE_ID, USER))
                .thenReturn(Optional.of(rule(RecurringStatus.PAUSED)));
        service.resume(USER, RULE_ID);

        verify(execution).resumeCatchUp(RULE_ID);
        verify(query).detail(USER, RULE_ID);
    }

    @Test
    void deleteSoftDeletesAndDropsTheLedgerLinks() {
        RecurringTransaction rule = rule(RecurringStatus.ACTIVE);
        when(rules.findOwnedForUpdate(USER, RULE_ID)).thenReturn(Optional.of(rule));

        service.delete(USER, RULE_ID);

        assertThat(rule.isDeleted()).isTrue();
        verify(transactionService).unlinkRecurring(USER, RULE_ID);
    }

    // --- run-now guards -------------------------------------------------------

    @Test
    void runNowRejectsUnknownAndNonActiveRules() {
        when(rules.findByIdAndUserIdAndDeletedAtIsNull(RULE_ID, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.runNow(USER, RULE_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_NOT_FOUND));

        when(rules.findByIdAndUserIdAndDeletedAtIsNull(RULE_ID, USER))
                .thenReturn(Optional.of(rule(RecurringStatus.PAUSED)));
        assertThatThrownBy(() -> service.runNow(USER, RULE_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getFieldErrors())
                        .extracting("field").contains("status"));
    }

    @Test
    void runNowRejectsARuleThatHasNotStartedYet() {
        RecurringTransaction future = rule(RecurringStatus.ACTIVE);
        future.setStartDate(LocalDate.now().plusDays(10));
        future.setNextRunDate(LocalDate.now().plusDays(10));
        when(rules.findByIdAndUserIdAndDeletedAtIsNull(RULE_ID, USER)).thenReturn(Optional.of(future));

        assertThatThrownBy(() -> service.runNow(USER, RULE_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECURRING_NOT_DUE));
    }

    @Test
    void runNowHandsTheIdempotencyKeyToTheExecutor() {
        when(rules.findByIdAndUserIdAndDeletedAtIsNull(RULE_ID, USER))
                .thenReturn(Optional.of(rule(RecurringStatus.ACTIVE)));
        RecurringRunResponse expected = new RecurringRunResponse(1L, NEXT, Instant.now(),
                RecurringRunStatus.SUCCESS, 10L, (short) 1, null);
        when(execution.runNow(USER, RULE_ID, "key-1")).thenReturn(expected);

        assertThat(service.runNow(USER, RULE_ID, "key-1")).isSameAs(expected);
        verify(execution).runNow(USER, RULE_ID, "key-1");
    }

    // --- helpers --------------------------------------------------------------

    private RecurringTransaction rule(RecurringStatus status) {
        RecurringTransaction rule = RecurringTransaction.create(
                USER, accountEntity, null, categoryEntity,
                TransactionType.EXPENSE, TransactionDirection.OUTFLOW,
                1_500L, "USD", BigDecimal.ONE, 1_500L,
                "Rent", null,
                RecurrenceFrequency.MONTHLY, (short) 1, 1, null,
                false, PAST, null, PAST,
                true, (short) 3, null);
        ReflectionTestUtils.setField(rule, "id", RULE_ID);
        switch (status) {
            case PAUSED -> rule.pause();
            case CANCELLED -> rule.cancel();
            case COMPLETED -> rule.markCompleted();
            case FAILED -> rule.markFailure("boom");
            case ACTIVE -> {
            }
        }
        return rule;
    }

    private CreateRecurringRequest expenseRequest() {
        return expenseRequestBuilder().build();
    }

    private RequestBuilder expenseRequestBuilder() {
        return new RequestBuilder();
    }

    private void stubActiveAccount(long accountId) {
        when(accountQuery.detail(USER, accountId))
                .thenReturn(accountResponse(accountId, "USD", AccountStatus.ACTIVE));
    }

    private void stubCategory(long categoryId, Category.Type type) {
        when(categoryQuery.detail(USER, categoryId)).thenReturn(categoryResponse(categoryId, type));
    }

    private void stubCalculator(LocalDate next, int previewCount) {
        when(calculator.firstOnOrAfter(any(), any())).thenReturn(next);
        List<LocalDate> dates = java.util.stream.IntStream.range(0, previewCount)
                .mapToObj(i -> next.plusDays(i))
                .toList();
        when(calculator.preview(any(), any(), anyInt())).thenReturn(dates);
    }

    private static AccountResponse accountResponse(long id, String currency, AccountStatus status) {
        return new AccountResponse(id, "Checking", AccountType.BANK, currency, 0, null, null, null,
                null, null, null, null, null, null, false, status, 0, null, 0, 0, "USD", null);
    }

    private static CategoryResponse categoryResponse(long id, Category.Type type) {
        return new CategoryResponse(id, USER, null, "Groceries", type, null, "#336699",
                false, false, 0, false, Category.Status.ACTIVE, null, null, null);
    }

    /** Mutable stand-in for the CreateRecurringRequest record's 18 components. */
    private static final class RequestBuilder {
        private Long accountId = ACCOUNT_ID;
        private Long toAccountId;
        private Long categoryId = CATEGORY_ID;
        private TransactionType type = TransactionType.EXPENSE;
        private Long amountMinor = 1_500L;
        private String currencyCode = "USD";
        private String description = "Rent";
        private String notes;
        private RecurrenceFrequency frequency = RecurrenceFrequency.MONTHLY;
        private Integer intervalCount = 1;
        private Integer dayOfMonth = 1;
        private Integer dayOfWeek;
        private Boolean skipWeekends;
        private LocalDate startDate = PAST;
        private LocalDate endDate;
        private Boolean autoPost;
        private Integer maxRetries;
        private LocalDate targetOccurrenceDate;

        RequestBuilder type(TransactionType type) {
            this.type = type;
            return this;
        }

        RequestBuilder toAccountId(Long toAccountId) {
            this.toAccountId = toAccountId;
            return this;
        }

        RequestBuilder categoryId(Long categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        RequestBuilder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        CreateRecurringRequest build() {
            return new CreateRecurringRequest(accountId, toAccountId, categoryId, type, amountMinor,
                    currencyCode, description, notes, frequency, intervalCount, dayOfMonth, dayOfWeek,
                    skipWeekends, startDate, endDate, autoPost, maxRetries, targetOccurrenceDate);
        }
    }
}
