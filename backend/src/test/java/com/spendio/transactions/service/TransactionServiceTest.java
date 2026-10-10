package com.spendio.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.ConversionResult;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.BulkCategoryRequest;
import com.spendio.transactions.dto.request.BulkDeleteRequest;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.dto.request.UpdateTransactionRequest;
import com.spendio.transactions.dto.response.BulkUpdateResponse;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.mapper.TransactionMapper;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.support.DedupeKeyFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final long USER = 42L;

    @Mock
    private TransactionRepository transactions;
    @Mock
    private AccountQueryService accountQuery;
    @Mock
    private CategoryQueryService categoryQuery;
    @Mock
    private ObjectProvider<FxConversionPort> fxPort;
    @Mock
    private BaseCurrencyPort baseCurrency;
    @Mock
    private jakarta.persistence.EntityManager entityManager;
    @Mock
    private TransactionMapper mapper;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactions, accountQuery, categoryQuery, fxPort, baseCurrency,
                entityManager, mapper, org.mockito.Mockito.mock(ApplicationEventPublisher.class), 0);
    }

    // --- create ---------------------------------------------------------------

    @Test
    void createIncomeDerivesInflowAndFreezesTheIdentityRate() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.INCOME));

        Transaction tx = captureSaved(createRequest(5L, 9L, TransactionType.INCOME, 250_00L, "USD", null));

        assertThat(tx.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(tx.getDirection()).isEqualTo(TransactionDirection.INFLOW);
        assertThat(tx.getAmountMinor()).isEqualTo(250_00L);
        assertThat(tx.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(tx.getBaseAmountMinor()).isEqualTo(250_00L);
        assertThat(tx.getFxRateId()).isNull();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx.getDedupeKey()).hasSize(64);
    }

    @Test
    void createRejectsArchivedAccount() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ARCHIVED));

        assertThatThrownBy(() -> service.create(USER, createRequest(5L, 9L, TransactionType.INCOME,
                100L, "USD", null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED));
    }

    @Test
    void createRejectsCurrencyThatDiffersFromTheAccount() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.create(USER, createRequest(5L, 9L, TransactionType.INCOME,
                100L, "EUR", null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("currencyCode");
                });
    }

    @Test
    void createRequiresACategoryForIncome() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.create(USER, createRequest(5L, null, TransactionType.INCOME,
                100L, "USD", null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("categoryId");
                });
    }

    @Test
    void createRejectsCategoryOfTheWrongType() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.EXPENSE));

        assertThatThrownBy(() -> service.create(USER, createRequest(5L, 9L, TransactionType.INCOME,
                100L, "USD", null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void createAdjustmentRequiresAnExplicitDirection() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));

        CreateTransactionRequest request = new CreateTransactionRequest(5L, null, TransactionType.ADJUSTMENT,
                null, 100L, "USD", null, LocalDate.of(2026, 6, 1), null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(USER, request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("direction");
                });
    }

    @Test
    void createAdjustmentRejectsACategory() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.create(USER, createRequest(5L, 9L, TransactionType.ADJUSTMENT,
                100L, "USD", null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("categoryId");
                });
    }

    @Test
    void createRejectsTransferTypeOutOfTheGenericEndpoint() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.create(USER, createRequest(5L, null, TransactionType.TRANSFER,
                100L, "USD", null), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("type");
                });
    }

    @Test
    void createRejectsAnAbsurdFutureDate() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.INCOME));

        CreateTransactionRequest request = new CreateTransactionRequest(5L, 9L, TransactionType.INCOME,
                null, 100L, "USD", null, LocalDate.now().plusDays(2), "later", null, null, null, null, null);

        assertThatThrownBy(() -> service.create(USER, request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("occurredOn");
                });
    }

    @Test
    void createFreezesCrossCurrencyRateFromTheDailySeries() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "EUR", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.EXPENSE));
        FxConversionPort port = fxPortAvailable();
        when(port.convert(100_00L, "EUR", "USD", LocalDate.of(2026, 6, 1)))
                .thenReturn(new ConversionResult(new BigDecimal("1.1"), 110_00L, 7L,
                        LocalDate.of(2026, 6, 1), false));

        Transaction tx = captureSaved(createRequest(5L, 9L, TransactionType.EXPENSE, 100_00L, "EUR",
                LocalDate.of(2026, 6, 1)));

        assertThat(tx.getBaseAmountMinor()).isEqualTo(110_00L);
        assertThat(tx.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.9090909091"));
        assertThat(tx.getFxRateId()).isEqualTo(7L);
    }

    @Test
    void createHonoursAnExplicitExchangeRateOverride() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "EUR", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.EXPENSE));
        FxConversionPort port = fxPortAvailable();
        when(port.convertWithRate(100_00L, "EUR", "USD", new BigDecimal("2.0000000000")))
                .thenReturn(new ConversionResult(new BigDecimal("2.0000000000"), 200_00L, null, null, false));

        CreateTransactionRequest request = new CreateTransactionRequest(5L, 9L, TransactionType.EXPENSE,
                null, 100_00L, "EUR", new BigDecimal("0.5"), LocalDate.of(2026, 6, 1),
                "coffee", null, null, null, null, null);
        Transaction tx = captureSaved(request);

        assertThat(tx.getBaseAmountMinor()).isEqualTo(200_00L);
        assertThat(tx.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(tx.getFxRateId()).isNull();
    }

    @Test
    void createReplaysAnExistingRowForTheSameIdempotencyKey() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        Transaction existing = expenseTx(11L);
        String token = DedupeKeyFactory.idempotencyKey(USER, "req-1");
        when(transactions.findFirstByUserIdAndDedupeKeyAndDeletedAtIsNullOrderByIdDesc(USER, token))
                .thenReturn(Optional.of(existing));

        service.create(USER, createRequest(5L, 9L, TransactionType.INCOME, 100L, "USD", null), "req-1");

        verify(transactions, never()).save(any());
        verify(accountQuery, never()).detail(any(), any());
    }

    @Test
    void createRejectsAnExplicitDuplicateDedupeKey() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.INCOME));
        when(transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(USER, "tok")).thenReturn(true);

        CreateTransactionRequest request = new CreateTransactionRequest(5L, 9L, TransactionType.INCOME,
                null, 100L, "USD", null, LocalDate.of(2026, 6, 1), "dup", null, null, null, null, "tok");

        assertThatThrownBy(() -> service.create(USER, request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_DUPLICATE));
    }

    @Test
    void createStoresNoAutoKeyWhenTheContentHashIsTaken() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.INCOME));
        String candidate = DedupeKeyFactory.contentKey(USER, 5L, LocalDate.of(2026, 6, 1), 100L, "USD", "twice");
        when(transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(USER, candidate)).thenReturn(true);

        CreateTransactionRequest request = new CreateTransactionRequest(5L, 9L, TransactionType.INCOME,
                null, 100L, "USD", null, LocalDate.of(2026, 6, 1), "twice", null, null, null, null, null);
        Transaction tx = captureSaved(request);

        assertThat(tx.getDedupeKey()).isNull();
    }

    @Test
    void createRefusesTheReservedOpeningDedupePrefix() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.INCOME));

        CreateTransactionRequest request = new CreateTransactionRequest(5L, 9L, TransactionType.INCOME,
                null, 100L, "USD", null, LocalDate.of(2026, 6, 1), "hax", null, null, null, null,
                "opening:sneaky");

        assertThatThrownBy(() -> service.create(USER, request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("dedupeKey");
                });
    }

    // --- update ---------------------------------------------------------------

    @Test
    void updateRejectsTransferLegsAndReversedRows() {
        Transaction transfer = Transaction.create(USER, account(5L, "USD"), null, TransactionType.TRANSFER,
                TransactionDirection.OUTFLOW, 100L, "USD", BigDecimal.ONE, 100L, null,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "x", null, null, null, null);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.update(USER, 1L, new UpdateTransactionRequest(null, null, null,
                null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_IMMUTABLE));

        Transaction reversed = expenseTx(2L);
        ReflectionTestUtils.setField(reversed, "reversedAt", Instant.now());
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(2L, USER)).thenReturn(Optional.of(reversed));

        assertThatThrownBy(() -> service.update(USER, 2L, new UpdateTransactionRequest(null, null, null,
                null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_IMMUTABLE));
    }

    @Test
    void updateWithAStaleVersionIsAConflict() {
        Transaction tx = expenseTx(1L);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> service.update(USER, 1L, new UpdateTransactionRequest(null, null, null,
                null, null, null, "changed", 9)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void updateChangingTheAmountRefreezesCrossCurrency() {
        Transaction tx = Transaction.create(USER, account(5L, "EUR"), null, TransactionType.ADJUSTMENT,
                TransactionDirection.INFLOW, 100_00L, "EUR", new BigDecimal("0.9"), 90_00L, null,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, null, null, null, null, null);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(tx));
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        FxConversionPort port = fxPortAvailable();
        when(port.convert(150_00L, "EUR", "USD", LocalDate.of(2026, 6, 1)))
                .thenReturn(new ConversionResult(new BigDecimal("1.08"), 162_00L, 3L,
                        LocalDate.of(2026, 6, 1), false));

        service.update(USER, 1L, new UpdateTransactionRequest(null, null, 150_00L, null, null, null, null, null));

        assertThat(tx.getAmountMinor()).isEqualTo(150_00L);
        assertThat(tx.getBaseAmountMinor()).isEqualTo(162_00L);
        assertThat(tx.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.9259259259"));
        assertThat(tx.getFxRateId()).isEqualTo(3L);
    }

    @Test
    void updateMovingToAnAccountWithAnotherCurrencyIsRejected() {
        Transaction tx = expenseTx(1L);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(tx));
        when(accountQuery.detail(USER, 7L)).thenReturn(accountResponse(7L, "USD", AccountStatus.ACTIVE));

        assertThatThrownBy(() -> service.update(USER, 1L, new UpdateTransactionRequest(7L, null, null,
                "EUR", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("accountId");
                });
    }

    @Test
    void updateMovingToAnArchivedAccountIsRejected() {
        Transaction tx = expenseTx(1L);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(tx));
        when(accountQuery.detail(USER, 7L)).thenReturn(accountResponse(7L, "USD", AccountStatus.ARCHIVED));

        assertThatThrownBy(() -> service.update(USER, 1L, new UpdateTransactionRequest(7L, null, null,
                null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED));
    }

    @Test
    void updateApppliesDescriptionNotesAndDate() {
        Transaction tx = expenseTx(1L);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(tx));
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");

        TransactionResponse response = service.update(USER, 1L, new UpdateTransactionRequest(null, null,
                null, null, LocalDate.of(2026, 7, 4), "  Roast  ", "   ", null));

        assertThat(tx.getOccurredOn()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(tx.getDescription()).isEqualTo("Roast");
        assertThat(tx.getNotes()).isNull();
        assertThat(response).isNull();
    }

    // --- delete ---------------------------------------------------------------

    @Test
    void deleteSoftDeletesAnEditableRow() {
        Transaction tx = expenseTx(1L);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(tx));

        service.delete(USER, 1L);

        assertThat(tx.isDeleted()).isTrue();
    }

    @Test
    void deleteRefusesTransferLegs() {
        Transaction transfer = Transaction.create(USER, account(5L, "USD"), null, TransactionType.TRANSFER,
                TransactionDirection.OUTFLOW, 100L, "USD", BigDecimal.ONE, 100L, null,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "x", null, null, null, null);
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(1L, USER)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.delete(USER, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_IMMUTABLE));
    }

    // --- bulk -----------------------------------------------------------------

    @Test
    void recategoriseOnlyTouchesEligibleRows() {
        Transaction income = expenseTx(1L);
        Transaction adjustment = Transaction.create(USER, account(5L, "USD"), null, TransactionType.ADJUSTMENT,
                TransactionDirection.INFLOW, 100L, "USD", BigDecimal.ONE, 100L, null,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, null, null, null, null, null);
        Transaction transfer = Transaction.create(USER, account(5L, "USD"), null, TransactionType.TRANSFER,
                TransactionDirection.OUTFLOW, 100L, "USD", BigDecimal.ONE, 100L, null,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "t", null, null, null, null);
        Transaction reversed = expenseTx(3L);
        ReflectionTestUtils.setField(reversed, "reversedAt", Instant.now());
        when(transactions.findAllById(any())).thenReturn(List.of(income, adjustment, transfer, reversed));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.EXPENSE));
        Category category = category(9L, Category.Type.EXPENSE);
        when(entityManager.getReference(Category.class, 9L)).thenReturn(category);

        BulkUpdateResponse response = service.recategorise(USER,
                new BulkCategoryRequest(List.of(1L, 2L, 3L, 4L), 9L));

        assertThat(response.updated()).isEqualTo(1);
        assertThat(income.getCategory()).isSameAs(category);
    }

    @Test
    void recategoriseRejectsACategoryTypeMismatch() {
        Transaction income = Transaction.create(USER, account(5L, "USD"), category(8L, Category.Type.INCOME),
                TransactionType.INCOME, TransactionDirection.INFLOW, 100L, "USD", BigDecimal.ONE, 100L,
                null, LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "wages", null, null, null, null);
        when(transactions.findAllById(any())).thenReturn(List.of(income));
        when(categoryQuery.detail(USER, 9L)).thenReturn(categoryResponse(9L, Category.Type.EXPENSE));

        assertThatThrownBy(() -> service.recategorise(USER,
                new BulkCategoryRequest(List.of(1L), 9L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void bulkWithAnUnknownIdIsNotFound() {
        when(transactions.findAllById(any())).thenReturn(List.of(expenseTx(1L)));

        assertThatThrownBy(() -> service.bulkDelete(USER, new BulkDeleteRequest(List.of(1L, 999L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    @Test
    void bulkDeleteSoftDeletesEligibleRowsAndSkipsImmutableOnes() {
        Transaction first = expenseTx(1L);
        Transaction transfer = Transaction.create(USER, account(5L, "USD"), null, TransactionType.TRANSFER,
                TransactionDirection.OUTFLOW, 100L, "USD", BigDecimal.ONE, 100L, null,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "t", null, null, null, null);
        when(transactions.findAllById(any())).thenReturn(List.of(first, transfer));

        BulkUpdateResponse response = service.bulkDelete(USER, new BulkDeleteRequest(List.of(1L, 2L)));

        assertThat(response.updated()).isEqualTo(1);
        assertThat(first.isDeleted()).isTrue();
        assertThat(transfer.isDeleted()).isFalse();
    }

    // --- helpers --------------------------------------------------------------

    private Transaction captureSaved(CreateTransactionRequest request) {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        service.create(USER, request, null);
        verify(transactions).save(captor.capture());
        return captor.getValue();
    }

    private FxConversionPort fxPortAvailable() {
        FxConversionPort port = org.mockito.Mockito.mock(FxConversionPort.class);
        when(fxPort.getIfAvailable()).thenReturn(port);
        return port;
    }

    private static CreateTransactionRequest createRequest(Long accountId, Long categoryId, TransactionType type,
            long amount, String currency, LocalDate occurredOn) {
        TransactionDirection direction = type == TransactionType.ADJUSTMENT
                ? TransactionDirection.INFLOW : null;
        return new CreateTransactionRequest(accountId, categoryId, type, direction, amount, currency, null,
                occurredOn == null ? LocalDate.of(2026, 6, 1) : occurredOn, "coffee", null, null, null,
                null, null);
    }

    private static AccountResponse accountResponse(long id, String currency, AccountStatus status) {
        return new AccountResponse(id, "Checking", AccountType.BANK, currency, 0, null, null, null,
                null, null, null, null, null, null, false, status, 0, null, 0, 0, "USD", null);
    }

    private static CategoryResponse categoryResponse(long id, Category.Type type) {
        return new CategoryResponse(id, USER, null, "Groceries", type, null, "#FF0000", false, false,
                (short) 0, false, Category.Status.ACTIVE, null, null, null);
    }

    private static Account account(long id, String currency) {
        Account account = Account.create(USER, "Checking", AccountType.BANK, currency, 0, null);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static Category category(long id, Category.Type type) {
        Category category = Category.create(USER, "Groceries", type, null, null, "#FF0000", false);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private static Transaction expenseTx(long id) {
        Transaction tx = Transaction.create(USER, account(5L, "USD"), category(9L, Category.Type.EXPENSE),
                TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 100L, "USD", BigDecimal.ONE, 100L,
                null, LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "coffee", null, null, null, null);
        ReflectionTestUtils.setField(tx, "id", id);
        return tx;
    }
}
