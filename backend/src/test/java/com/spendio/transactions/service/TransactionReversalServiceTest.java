package com.spendio.transactions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.accounts.domain.Account;
import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.ReverseTransactionRequest;
import com.spendio.transactions.dto.response.TransferResponse;
import com.spendio.transactions.mapper.TransactionMapper;
import com.spendio.transactions.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reversals (ARCHITECTURE.md Phase 7, D.8): one mirrored entry per original with the
 * direction flipped and the same frozen FX triple (so the pair nets to zero in base),
 * single-shot guards, and the atomic two-leg transfer flip.
 */
@ExtendWith(MockitoExtension.class)
class TransactionReversalServiceTest {

    private static final long USER = 42L;
    private static final LocalDate DAY = LocalDate.of(2026, 6, 10);

    @Mock
    private TransactionRepository transactions;
    @Mock
    private BaseCurrencyPort baseCurrency;
    @Mock
    private TransactionMapper mapper;

    private TransactionReversalService service;

    @BeforeEach
    void setUp() {
        service = new TransactionReversalService(transactions, baseCurrency, mapper,
                org.mockito.Mockito.mock(ApplicationEventPublisher.class), 0);
    }

    // --- single row -----------------------------------------------------------

    @Test
    void createsAMirroredEntryAndMarksTheOriginalReversed() {
        Transaction original = row(5L, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 1_500L, day());
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).thenReturn(Optional.of(original));
        captureSaves();

        service.reverse(USER, 5L, new ReverseTransactionRequest("wrong charge", DAY));

        Transaction mirror = lastSaved();
        assertThat(mirror.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(mirror.getDirection()).isEqualTo(TransactionDirection.INFLOW);
        assertThat(mirror.getAmountMinor()).isEqualTo(1_500L);
        assertThat(mirror.getBaseAmountMinor()).isEqualTo(1_500L);
        assertThat(mirror.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(mirror.getCategory()).isSameAs(original.getCategory());
        assertThat(mirror.getReversalOfTransactionId()).isEqualTo(5L);
        assertThat(mirror.getReversalReason()).isEqualTo("wrong charge");
        assertThat(mirror.getOccurredOn()).isEqualTo(DAY);
        assertThat(mirror.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(mirror.getDedupeKey()).isNull();
        assertThat(original.getReversedAt()).isNotNull();
        assertThat(original.getReversalReason()).isEqualTo("wrong charge");
    }

    @Test
    void theMirroredPairNetsToZeroInTheBaseCurrency() {
        Transaction original = row(5L, TransactionType.INCOME, TransactionDirection.INFLOW, 9_900L, day());
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).thenReturn(Optional.of(original));
        captureSaves();

        service.reverse(USER, 5L, new ReverseTransactionRequest("refund", DAY));

        Transaction mirror = lastSaved();
        long originalSigned = original.getDirection() == TransactionDirection.INFLOW
                ? original.getBaseAmountMinor() : -original.getBaseAmountMinor();
        long mirrorSigned = mirror.getDirection() == TransactionDirection.INFLOW
                ? mirror.getBaseAmountMinor() : -mirror.getBaseAmountMinor();
        assertThat(originalSigned + mirrorSigned).isZero();
    }

    @Test
    void refusesToReverseTheSameRowTwice() {
        Transaction original = row(5L, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 100L, day());
        original.markReversed(Instant.now(), "first");

        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.reverse(USER, 5L, new ReverseTransactionRequest("again", DAY)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_ALREADY_REVERSED));
        verify(transactions, never()).save(any());
    }

    @Test
    void refusesToReverseAReversalEntry() {
        Transaction mirror = row(5L, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 100L, day());
        mirror.markAsReversalOf(3L, "original reason");

        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).thenReturn(Optional.of(mirror));

        assertThatThrownBy(() -> service.reverse(USER, 5L, new ReverseTransactionRequest("again", DAY)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_NOT_REVERSIBLE));
        verify(transactions, never()).save(any());
    }

    @Test
    void refusesToReverseATransferLegOutsideItsGroup() {
        Transaction leg = row(5L, TransactionType.TRANSFER, TransactionDirection.OUTFLOW, 100L, day());
        leg.linkTransfer("group-1", 6L);

        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).thenReturn(Optional.of(leg));

        assertThatThrownBy(() -> service.reverse(USER, 5L, new ReverseTransactionRequest("x", DAY)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_IMMUTABLE));
        verify(transactions, never()).save(any());
    }

    @Test
    void refusesToReverseAVoidRow() {
        Transaction original = row(5L, TransactionType.EXPENSE, TransactionDirection.OUTFLOW, 100L, day());
        ReflectionTestUtils.setField(original, "status", TransactionStatus.VOID);

        when(transactions.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.reverse(USER, 5L, new ReverseTransactionRequest("x", DAY)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_NOT_REVERSIBLE));
        verify(transactions, never()).save(any());
    }

    // --- transfer group -------------------------------------------------------

    @Test
    void reversesBothLegsOfATransferAtomically() {
        Transaction fromLeg = row(11L, TransactionType.TRANSFER, TransactionDirection.OUTFLOW, 100_00L, day());
        Transaction toLeg = row(12L, TransactionType.TRANSFER, TransactionDirection.INFLOW, 100_00L, day());
        fromLeg.linkTransfer("group-1", 12L);
        toLeg.linkTransfer("group-1", 11L);
        when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        when(transactions.findByUserIdAndTransferGroupIdAndDeletedAtIsNull(USER, "group-1"))
                .thenReturn(List.of(toLeg, fromLeg));
        AtomicLong nextId = new AtomicLong(50L);
        when(transactions.save(any())).thenAnswer(inv -> {
            Transaction saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", nextId.getAndIncrement());
            return saved;
        });
        when(mapper.toTransferResponse(eq("group-1"), anyList(), eq("USD")))
                .thenReturn(new TransferResponse("group-1", null, null));

        TransferResponse response = service.reverseTransfer(USER, "group-1",
                new ReverseTransactionRequest("sent to the wrong account", DAY));

        List<Transaction> mirrors = savedRows();
        assertThat(mirrors).hasSize(2);
        Transaction mirrorFrom = mirrors.get(0);
        Transaction mirrorTo = mirrors.get(1);
        assertThat(mirrorFrom.getDirection()).isEqualTo(TransactionDirection.INFLOW);
        assertThat(mirrorTo.getDirection()).isEqualTo(TransactionDirection.OUTFLOW);
        assertThat(mirrorFrom.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(mirrorTo.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(mirrorFrom.getTransferGroupId()).isEqualTo("group-1");
        assertThat(mirrorTo.getTransferGroupId()).isEqualTo("group-1");
        assertThat(mirrorFrom.getReversalOfTransactionId()).isEqualTo(11L);
        assertThat(mirrorTo.getReversalOfTransactionId()).isEqualTo(12L);
        assertThat(mirrorFrom.getCounterpartyTransactionId()).isEqualTo(mirrorTo.getId());
        assertThat(mirrorTo.getCounterpartyTransactionId()).isEqualTo(mirrorFrom.getId());
        assertThat(mirrorFrom.getReversalReason()).isEqualTo("sent to the wrong account");
        assertThat(fromLeg.getReversedAt()).isNotNull();
        assertThat(toLeg.getReversedAt()).isNotNull();
        assertThat(response).isNotNull();
        verify(mapper).toTransferResponse(eq("group-1"), anyList(), eq("USD"));
    }

    @Test
    void refusesAGroupReverseWhenEitherLegIsAlreadyReversed() {
        Transaction fromLeg = row(11L, TransactionType.TRANSFER, TransactionDirection.OUTFLOW, 100_00L, day());
        Transaction toLeg = row(12L, TransactionType.TRANSFER, TransactionDirection.INFLOW, 100_00L, day());
        toLeg.markReversed(Instant.now(), "earlier");
        when(transactions.findByUserIdAndTransferGroupIdAndDeletedAtIsNull(USER, "group-1"))
                .thenReturn(List.of(fromLeg, toLeg));

        assertThatThrownBy(() -> service.reverseTransfer(USER, "group-1",
                new ReverseTransactionRequest("again", DAY)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_ALREADY_REVERSED));
        verify(transactions, never()).save(any());
    }

    @Test
    void unknownTransferGroupIsNotFound() {
        when(transactions.findByUserIdAndTransferGroupIdAndDeletedAtIsNull(USER, "missing"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.reverseTransfer(USER, "missing",
                new ReverseTransactionRequest("x", DAY)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    // --- helpers --------------------------------------------------------------

    private List<Transaction> savedRows() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return new ArrayList<>(captor.getAllValues());
    }

    private Transaction lastSaved() {
        List<Transaction> rows = savedRows();
        return rows.get(rows.size() - 1);
    }

    private void captureSaves() {
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static LocalDate day() {
        return LocalDate.of(2026, 6, 1);
    }

    private static Transaction row(Long id, TransactionType type, TransactionDirection direction, long amount,
            LocalDate occurredOn) {
        Transaction tx = Transaction.create(
                USER,
                account(),
                category(),
                type,
                direction,
                amount,
                "USD",
                BigDecimal.ONE,
                amount,
                null,
                occurredOn,
                TransactionStatus.POSTED,
                "Original",
                null,
                null,
                "original notes",
                null);
        ReflectionTestUtils.setField(tx, "id", id);
        return tx;
    }

    private static Account account() {
        return org.mockito.Mockito.mock(Account.class);
    }

    private static Category category() {
        return org.mockito.Mockito.mock(Category.class);
    }
}
