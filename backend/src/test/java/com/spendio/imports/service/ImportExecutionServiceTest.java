package com.spendio.imports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.domain.ImportOptions;
import com.spendio.imports.dto.request.ExecuteImportRequest;
import com.spendio.imports.dto.response.ImportReportResponse;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.ImportCompletedEvent;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Execution engine (ARCHITECTURE.md D.12 step 8): every row posts in its own committed
 * transaction, partial failures are counted instead of aborting when allowed, a disabled
 * partial import undoes what it already posted, and a dry run touches nothing.
 */
@ExtendWith(MockitoExtension.class)
class ImportExecutionServiceTest {

    private static final long USER = 42L;
    private static final long JOB_ID = 77L;
    private static final long ACCOUNT_ID = 5L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 9);

    @Mock
    private CsvImportService importService;
    @Mock
    private CsvImportRowRepository rows;
    @Mock
    private TransactionService transactionService;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ImportExecutionService service;
    private CsvImport job;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new ImportExecutionService(importService, rows, transactionService,
                events, transactionManager);
        job = CsvImport.uploaded(USER, "bank.csv", 100, "key", "sha", ImportOptions.empty());
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        when(importService.getOwned(USER, JOB_ID)).thenReturn(job);
    }

    private void readyJob(int totalRows) {
        job.markParsed(",", "UTF-8", 1, List.of("Date", "Amount", "Description"), totalRows);
        job.markReady();
    }

    private CsvImportRow row(int number, String description, long amountMinor) {
        CsvImportRow row = CsvImportRow.pending(JOB_ID, number,
                Map.of("Date", "2026-03-09", "Amount", "-10.00", "Description", description));
        row.storeParsed(Map.of("occurredOn", "2026-03-09"), DAY, amountMinor, "EUR",
                description, "EXPENSE", List.of());
        row.setMatchedAccount(ACCOUNT_ID, "EXACT", BigDecimal.ONE);
        return row;
    }

    private TransactionResponse txResponse(long id) {
        return new TransactionResponse(id, null, null, null, null, 0, "EUR", null, 0, "EUR",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void createsEveryReadyRowAndPublishesTheCompletionEvent() {
        readyJob(2);
        CsvImportRow first = row(1, "Coffee", -1000);
        CsvImportRow second = row(2, "Lunch", -1250);
        when(rows.findByImportIdAndStatusIn(eq(JOB_ID), anyCollection()))
                .thenReturn(List.of(first, second));
        when(transactionService.createForCsvImport(eq(USER), any(CreateTransactionRequest.class),
                eq(JOB_ID), isNull(), eq("import:" + JOB_ID + ":1"))).thenReturn(txResponse(101L));
        when(transactionService.createForCsvImport(eq(USER), any(CreateTransactionRequest.class),
                eq(JOB_ID), isNull(), eq("import:" + JOB_ID + ":2"))).thenReturn(txResponse(102L));

        ImportReportResponse report = service.execute(USER, JOB_ID, new ExecuteImportRequest(null, null, null));

        assertThat(report.createdTransactions())
                .extracting(ImportReportResponse.TransactionLine::id)
                .containsExactly(101L, 102L);
        assertThat(report.failures()).isEmpty();
        assertThat(first.getStatus()).isEqualTo(ImportRowStatus.CREATED);
        assertThat(second.getStatus()).isEqualTo(ImportRowStatus.CREATED);
        assertThat(first.getCreatedTransactionId()).isEqualTo(101L);
        assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(job.getCreatedRows()).isEqualTo(2);

        ArgumentCaptor<ImportCompletedEvent> captor = ArgumentCaptor.forClass(ImportCompletedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().createdRows()).isEqualTo(2);
        assertThat(captor.getValue().failedRows()).isZero();
        assertThat(captor.getValue().dedupeKey()).isEqualTo("import:" + JOB_ID);
    }

    @Test
    void dryRunReportsWithoutTouchingTheLedger() {
        readyJob(1);
        CsvImportRow first = row(1, "Coffee", -1000);
        when(rows.findByImportIdAndStatusIn(eq(JOB_ID), anyCollection())).thenReturn(List.of(first));

        ImportReportResponse report = service.execute(USER, JOB_ID,
                new ExecuteImportRequest(null, null, true));

        assertThat(report.createdTransactions()).isEmpty();
        assertThat(report.failures()).hasSize(1);
        assertThat(report.failures().getFirst().reason()).contains("dry-run");
        verify(transactionService, never()).createForCsvImport(any(), any(), any(), any(), any());
        verify(events, never()).publishEvent(any(Object.class));
        assertThat(job.getStatus()).isEqualTo(ImportStatus.READY);
    }

    @Test
    void partialFailureIsCountedAndTheImportStillCompletes() {
        readyJob(2);
        CsvImportRow first = row(1, "Coffee", -1000);
        CsvImportRow second = row(2, "Bad row", -1250);
        when(rows.findByImportIdAndStatusIn(eq(JOB_ID), anyCollection()))
                .thenReturn(List.of(first, second));
        when(transactionService.createForCsvImport(eq(USER), any(CreateTransactionRequest.class),
                eq(JOB_ID), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "account is archived"))
                .thenReturn(txResponse(102L));

        ImportReportResponse report = service.execute(USER, JOB_ID, new ExecuteImportRequest(null, null, null));

        assertThat(report.createdTransactions()).hasSize(1);
        assertThat(report.failures()).hasSize(1);
        assertThat(report.failures().getFirst().reason()).isEqualTo("account is archived");
        assertThat(first.getStatus()).isEqualTo(ImportRowStatus.FAILED);
        assertThat(second.getStatus()).isEqualTo(ImportRowStatus.CREATED);
        assertThat(job.getStatus()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(job.getFailedRows()).isEqualTo(1);
        assertThat(job.getCreatedRows()).isEqualTo(1);
        verify(events).publishEvent(any(ImportCompletedEvent.class));
    }

    @Test
    void disabledPartialImportAbortsAndUndoesTheCommittedRows() {
        readyJob(2);
        CsvImportRow first = row(1, "Coffee", -1000);
        CsvImportRow second = row(2, "Bad row", -1250);
        when(rows.findByImportIdAndStatusIn(eq(JOB_ID), anyCollection()))
                .thenReturn(List.of(first, second));
        when(transactionService.createForCsvImport(eq(USER), any(CreateTransactionRequest.class),
                eq(JOB_ID), isNull(), any()))
                .thenReturn(txResponse(101L))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST, "account is archived"));
        Transaction committed = mock(Transaction.class);
        doAnswer(inv -> {
            committed.softDelete();
            return null;
        }).when(transactionService)
                .undoImportedTransactions(eq(USER), eq(List.of(101L)));

        assertThatThrownBy(() -> service.execute(USER, JOB_ID, new ExecuteImportRequest(null, false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMPORT_MAPPING_INVALID);

        verify(committed).softDelete();
        verify(transactionService).undoImportedTransactions(USER, List.of(101L));
        assertThat(second.getStatus()).isEqualTo(ImportRowStatus.FAILED);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsAnImportThatIsNotReady() {
        job.markParsing();

        assertThatThrownBy(() -> service.execute(USER, JOB_ID, new ExecuteImportRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMPORT_NOT_READY);
    }

    @Test
    void rejectsWhenNoRowsAreTargeted() {
        readyJob(2);
        when(rows.findByImportIdAndStatusIn(eq(JOB_ID), anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.execute(USER, JOB_ID, new ExecuteImportRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No rows are ready");
    }

    @Test
    void skipDuplicatesTargetsOnlyConfirmedAndValidRows() {
        readyJob(3);
        when(rows.findByImportIdAndStatusIn(eq(JOB_ID), anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.execute(USER, JOB_ID, new ExecuteImportRequest(null, null, null)))
                .isInstanceOf(BusinessException.class);

        verify(rows).findByImportIdAndStatusIn(eq(JOB_ID),
                argThat(statuses -> statuses.contains(ImportRowStatus.CONFIRMED)
                        && statuses.contains(ImportRowStatus.VALID)
                        && !statuses.contains(ImportRowStatus.DUPLICATE)));
    }

    @Test
    void explicitRowIdsScopeTheExecution() {
        readyJob(3);
        CsvImportRow target = row(7, "Coffee", -1000);
        when(rows.findByImportIdAndStatusInAndIdIn(eq(JOB_ID), anyCollection(), eq(List.of(77L))))
                .thenReturn(List.of(target));
        when(transactionService.createForCsvImport(eq(USER), any(CreateTransactionRequest.class),
                eq(JOB_ID), isNull(), eq("import:" + JOB_ID + ":7"))).thenReturn(txResponse(101L));

        ImportReportResponse report = service.execute(USER, JOB_ID,
                new ExecuteImportRequest(List.of(77L), null, null));

        assertThat(report.createdTransactions()).hasSize(1);
        verify(rows, never()).findByImportIdAndStatusIn(eq(JOB_ID), anyCollection());
    }
}