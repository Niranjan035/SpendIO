package com.spendio.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
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
import com.spendio.transactions.dto.request.CreateTransferRequest;
import com.spendio.transactions.dto.response.TransferResponse;
import com.spendio.transactions.mapper.TransactionMapper;
import com.spendio.transactions.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Transfers (ARCHITECTURE.md Phase 7, D.8/E.8): linked pairs sharing a group id and
 * counterparty links, same-currency equality, one-shot cross-currency conversion with a
 * frozen FX triple per leg, and the field-level validation rules on create.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final long USER = 42L;
    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    @Mock
    private TransactionRepository transactions;
    @Mock
    private AccountQueryService accountQuery;
    @Mock
    private ObjectProvider<FxConversionPort> fxPort;
    @Mock
    private BaseCurrencyPort baseCurrency;
    @Mock
    private EntityManager entityManager;
    @Mock
    private TransactionMapper mapper;
    @Mock
    private Account fromAccountEntity;
    @Mock
    private Account toAccountEntity;

    private TransferService service;
    private final List<Transaction> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new TransferService(transactions, accountQuery, fxPort, baseCurrency, entityManager, mapper,
                mock(ApplicationEventPublisher.class), 0);
        saved.clear();
    }

    @Test
    void createsALinkedPairForSameCurrencyTransfers() {
        stubAccounts("USD", "USD");
        stubIdentitySaveAndMapper();

        TransferResponse response = service.create(USER,
                new CreateTransferRequest(5L, 6L, 20_000L, "USD", null, DAY, "Rent split", "  half and half  "));

        assertThat(saved).hasSize(2);
        Transaction fromLeg = saved.get(0);
        Transaction toLeg = saved.get(1);
        assertThat(fromLeg.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(toLeg.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(fromLeg.getDirection()).isEqualTo(TransactionDirection.OUTFLOW);
        assertThat(toLeg.getDirection()).isEqualTo(TransactionDirection.INFLOW);
        assertThat(fromLeg.getAmountMinor()).isEqualTo(20_000L);
        assertThat(toLeg.getAmountMinor()).isEqualTo(20_000L);
        assertThat(fromLeg.getCurrencyCode()).isEqualTo("USD");
        assertThat(toLeg.getCurrencyCode()).isEqualTo("USD");
        assertThat(fromLeg.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(toLeg.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(fromLeg.getBaseAmountMinor()).isEqualTo(20_000L);
        assertThat(toLeg.getBaseAmountMinor()).isEqualTo(20_000L);
        assertThat(fromLeg.getFxRateId()).isNull();
        assertThat(fromLeg.getCategory()).isNull();
        assertThat(fromLeg.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(fromLeg.getDedupeKey()).isNotBlank();
        assertThat(fromLeg.getDescription()).isEqualTo("Rent split");
        assertThat(fromLeg.getNotes()).isEqualTo("half and half");
        assertThat(fromLeg.getAccount()).isSameAs(fromAccountEntity);
        assertThat(toLeg.getAccount()).isSameAs(toAccountEntity);
        assertThat(fromLeg.getTransferGroupId()).isNotBlank();
        assertThat(toLeg.getTransferGroupId()).isEqualTo(fromLeg.getTransferGroupId());
        assertThat(fromLeg.getCounterpartyTransactionId()).isEqualTo(toLeg.getId());
        assertThat(toLeg.getCounterpartyTransactionId()).isEqualTo(fromLeg.getId());
        assertThat(response).isNotNull();
        ArgumentCaptor<String> groupId = ArgumentCaptor.forClass(String.class);
        verify(mapper).toTransferResponse(groupId.capture(), anyList(), eq("USD"));
        assertThat(fromLeg.getTransferGroupId()).isEqualTo(groupId.getValue());
    }

    @Test
    void convertsTheDestinationAmountOnceForACrossCurrencyTransfer() {
        FxConversionPort fx = mock(FxConversionPort.class);
        stubAccounts("USD", "EUR");
        when(fxPort.getIfAvailable()).thenReturn(fx);
        when(fx.convert(10_000L, "USD", "EUR", DAY))
                .thenReturn(new ConversionResult(new BigDecimal("0.91427"), 9_143L, 7L, DAY, false));
        when(fx.convert(9_143L, "EUR", "USD", DAY))
                .thenReturn(new ConversionResult(new BigDecimal("1.25"), 9_999L, 9L, DAY, false));
        stubIdentitySaveAndMapper();

        service.create(USER, new CreateTransferRequest(5L, 6L, 10_000L, "USD", null, DAY, "Travel fund", null));

        Transaction fromLeg = saved.get(0);
        Transaction toLeg = saved.get(1);
        assertThat(fromLeg.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(fromLeg.getBaseAmountMinor()).isEqualTo(10_000L);
        assertThat(fromLeg.getFxRateId()).isNull();
        assertThat(toLeg.getAmountMinor()).isEqualTo(9_143L);
        assertThat(toLeg.getCurrencyCode()).isEqualTo("EUR");
        assertThat(toLeg.getBaseAmountMinor()).isEqualTo(9_999L);
        assertThat(toLeg.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.8"));
        assertThat(toLeg.getFxRateId()).isEqualTo(9L);
        verify(fx).convert(10_000L, "USD", "EUR", DAY);
        verify(fx).convert(9_143L, "EUR", "USD", DAY);
    }

    @Test
    void usesTheExplicitDestinationAmountWithoutConverting() {
        FxConversionPort fx = mock(FxConversionPort.class);
        stubAccounts("USD", "EUR");
        when(fxPort.getIfAvailable()).thenReturn(fx);
        when(fx.convert(8_500L, "EUR", "USD", DAY))
                .thenReturn(new ConversionResult(new BigDecimal("1.25"), 10_000L, 9L, DAY, false));
        stubIdentitySaveAndMapper();

        service.create(USER, new CreateTransferRequest(5L, 6L, 10_000L, "USD", 8_500L, DAY, null, null));

        Transaction toLeg = saved.get(1);
        assertThat(toLeg.getAmountMinor()).isEqualTo(8_500L);
        assertThat(toLeg.getBaseAmountMinor()).isEqualTo(10_000L);
        assertThat(toLeg.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.8"));
        verify(fx, never()).convert(eq(10_000L), eq("USD"), eq("EUR"), any(LocalDate.class));
    }

    @Test
    void refusesTheSameAccountOnBothSides() {
        assertThatThrownBy(() -> service.create(USER,
                new CreateTransferRequest(5L, 5L, 100L, "USD", null, DAY, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("toAccountId");
                });
    }

    @Test
    void refusesACurrencyThatIsNotTheSourceAccountCurrency() {
        stubAccounts("USD", "USD");

        assertThatThrownBy(() -> service.create(USER,
                new CreateTransferRequest(5L, 6L, 100L, "EUR", null, DAY, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("currencyCode");
                });
    }

    @Test
    void refusesAnArchivedAccount() {
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, "USD", AccountStatus.ACTIVE));
        when(accountQuery.detail(USER, 6L)).thenReturn(accountResponse(6L, "USD", AccountStatus.ARCHIVED));

        assertThatThrownBy(() -> service.create(USER,
                new CreateTransferRequest(5L, 6L, 100L, "USD", null, DAY, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_ACCOUNT_ARCHIVED));
    }

    @Test
    void refusesAFutureDatedTransfer() {
        stubAccounts("USD", "USD");
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> service.create(USER,
                new CreateTransferRequest(5L, 6L, 100L, "USD", null, tomorrow, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("occurredOn");
                });
    }

    @Test
    void refusesMismatchedToAmountForSameCurrency() {
        stubAccounts("USD", "USD");

        assertThatThrownBy(() -> service.create(USER,
                new CreateTransferRequest(5L, 6L, 100L, "USD", 101L, DAY, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.getFieldErrors()).extracting("field").contains("toAmountMinor");
                });
    }

    @Test
    void unknownTransferGroupIsNotFound() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(transactions.findByUserIdAndTransferGroupIdInAndDeletedAtIsNullAndReversalOfTransactionIdIsNull(
                eq(USER), anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.detail(USER, "missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND);
                    assertThat(be.getProperties()).containsEntry("transferGroupId", "missing");
                });
    }

    // --- helpers --------------------------------------------------------------

    private void stubAccounts(String fromCurrency, String toCurrency) {
        when(accountQuery.detail(USER, 5L)).thenReturn(accountResponse(5L, fromCurrency, AccountStatus.ACTIVE));
        when(accountQuery.detail(USER, 6L)).thenReturn(accountResponse(6L, toCurrency, AccountStatus.ACTIVE));
        lenient().when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        lenient().when(entityManager.getReference(Account.class, 5L)).thenReturn(fromAccountEntity);
        lenient().when(entityManager.getReference(Account.class, 6L)).thenReturn(toAccountEntity);
    }

    private void stubIdentitySaveAndMapper() {
        when(transactions.save(any())).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            if (ReflectionTestUtils.getField(tx, "id") == null) {
                ReflectionTestUtils.setField(tx, "id", 100L + saved.size());
            }
            saved.add(tx);
            return tx;
        });
        when(mapper.toTransferResponse(anyString(), anyList(), eq("USD")))
                .thenReturn(new TransferResponse("stub", null, null));
    }

    private static AccountResponse accountResponse(long id, String currency, AccountStatus status) {
        return new AccountResponse(id, "Checking", AccountType.BANK, currency, 0, null, null, null,
                null, null, null, null, null, null, false, status, 0, null, 0, 0, "USD", null);
    }
}
