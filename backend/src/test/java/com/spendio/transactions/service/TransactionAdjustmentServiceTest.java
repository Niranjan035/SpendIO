package com.spendio.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.domain.Account;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.ConversionResult;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Adjustment writes (ARCHITECTURE.md Phase 7, D.8): ADJUSTMENT type, no category,
 * reason in notes, identity freeze in the base currency and a frozen FX triple for
 * anything else - or a fast FX_RATE_UNAVAILABLE when no fx port answers.
 */
@ExtendWith(MockitoExtension.class)
class TransactionAdjustmentServiceTest {

    private static final long USER = 42L;
    private static final long ACCOUNT = 7L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 1);

    @Mock
    private TransactionRepository transactions;
    @Mock
    private BaseCurrencyPort baseCurrency;
    @Mock
    private ObjectProvider<FxConversionPort> fxPort;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Account account;

    private TransactionAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new TransactionAdjustmentService(transactions, baseCurrency, fxPort, entityManager,
                mock(ApplicationEventPublisher.class));
    }

    @Test
    void writesAnAdjustmentRowWithTheReasonInNotes() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(entityManager.getReference(Account.class, ACCOUNT)).thenReturn(account);
        AtomicReference<Transaction> saved = captureSaved();

        service.adjust(USER, ACCOUNT, TransactionDirection.INFLOW, 2_500L, "USD", DAY, "  closing the books  ");

        Transaction tx = saved.get();
        assertThat(tx.getType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(tx.getCategory()).isNull();
        assertThat(tx.getDirection()).isEqualTo(TransactionDirection.INFLOW);
        assertThat(tx.getAmountMinor()).isEqualTo(2_500L);
        assertThat(tx.getCurrencyCode()).isEqualTo("USD");
        assertThat(tx.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(tx.getBaseAmountMinor()).isEqualTo(2_500L);
        assertThat(tx.getFxRateId()).isNull();
        assertThat(tx.getOccurredOn()).isEqualTo(DAY);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx.getNotes()).isEqualTo("closing the books");
        assertThat(tx.getDescription()).isNull();
        assertThat(tx.getDedupeKey()).isNull();
    }

    @Test
    void freezesTheFxTripleForACurrencyOtherThanTheBase() {
        FxConversionPort fx = mock(FxConversionPort.class);
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(fxPort.getIfAvailable()).thenReturn(fx);
        when(fx.convert(5_000L, "EUR", "USD", DAY))
                .thenReturn(new ConversionResult(new BigDecimal("1.10"), 5_500L, 9L, DAY, false));
        when(entityManager.getReference(Account.class, ACCOUNT)).thenReturn(account);
        AtomicReference<Transaction> saved = captureSaved();

        service.adjust(USER, ACCOUNT, TransactionDirection.OUTFLOW, 5_000L, "EUR", DAY, "fx check");

        Transaction tx = saved.get();
        assertThat(tx.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.9090909091"));
        assertThat(tx.getBaseAmountMinor()).isEqualTo(5_500L);
        assertThat(tx.getFxRateId()).isEqualTo(9L);
    }

    @Test
    void failsFastWithoutAnFxPortForACrossCurrencyRow() {
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(fxPort.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.adjust(USER, ACCOUNT, TransactionDirection.INFLOW,
                100L, "EUR", DAY, "x"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FX_RATE_UNAVAILABLE));
        verify(transactions, never()).save(any());
    }

    private AtomicReference<Transaction> captureSaved() {
        AtomicReference<Transaction> saved = new AtomicReference<>();
        when(transactions.save(any())).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        return saved;
    }
}
