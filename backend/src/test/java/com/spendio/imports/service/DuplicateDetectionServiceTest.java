package com.spendio.imports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.domain.ImportOptions;
import com.spendio.imports.dto.response.DuplicateGroupResponse;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.service.TransactionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Duplicate detection (ARCHITECTURE.md D.12 step 5): identical rows inside the file are
 * marked DUPLICATE against the first occurrence, and rows already in the ledger are
 * marked DUPLICATE against the existing transaction.
 */
@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    private static final long USER = 42L;
    private static final long JOB_ID = 77L;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 9);

    @Mock
    private CsvImportService importService;
    @Mock
    private CsvImportRowRepository rows;
    @Mock
    private TransactionService transactions;

    private DuplicateDetectionService service;
    private CsvImport job;

    @BeforeEach
    void setUp() {
        service = new DuplicateDetectionService(importService, rows, transactions);
        job = CsvImport.uploaded(USER, "bank.csv", 100, "key", "sha", ImportOptions.empty());
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        job.markParsed(",", "UTF-8", 1, List.of("Date", "Amount"), 3);
        job.requireMapping();
        when(importService.getOwned(USER, JOB_ID)).thenReturn(job);
    }

    private CsvImportRow valid(int number, long amountMinor, String description) {
        CsvImportRow row = CsvImportRow.pending(JOB_ID, number,
                Map.of("Date", "2026-03-09", "Amount", "-10.00"));
        row.storeParsed(Map.of(), DAY, amountMinor, "EUR", description, "EXPENSE", List.of());
        return row;
    }

    @Test
    void identicalRowsInFileAreMarkedAgainstTheFirstOccurrence() {
        CsvImportRow first = valid(1, -1000, "Coffee shop");
        CsvImportRow twin = valid(2, -1000, "Coffee shop");
        CsvImportRow other = valid(3, -5000, "Rent");
        when(rows.findByImportIdAndStatus(JOB_ID, ImportRowStatus.VALID))
                .thenReturn(List.of(first, twin, other));

        service.detect(USER, JOB_ID);

        assertThat(first.getStatus()).isEqualTo(ImportRowStatus.VALID);
        assertThat(twin.getStatus()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(twin.getDuplicateOfRowId()).isEqualTo(first.getId());
        assertThat(other.getStatus()).isEqualTo(ImportRowStatus.VALID);
        verify(rows).save(twin);
        verify(rows, never()).save(first);
    }

    @Test
    void rowsAlreadyInTheLedgerPointAtTheExistingTransaction() {
        CsvImportRow row = valid(1, -1000, "Coffee shop");
        when(rows.findByImportIdAndStatus(JOB_ID, ImportRowStatus.VALID)).thenReturn(List.of(row));
        when(transactions.existingTransactionIdForDedupeKey(anyLong(), any()))
                .thenReturn(Optional.of(555L));

        service.detect(USER, JOB_ID);

        assertThat(row.getStatus()).isEqualTo(ImportRowStatus.DUPLICATE);
        assertThat(row.getDuplicateOfTransactionId()).isEqualTo(555L);
        assertThat(row.getDuplicateOfRowId()).isNull();
    }

    @Test
    void otherStatusesAreLeftAlone() {
        job.markParsing();
        CsvImportRow pending = CsvImportRow.pending(JOB_ID, 1, Map.of("Date", "2026-03-09"));

        service.detect(USER, JOB_ID);

        assertThat(pending.getStatus()).isEqualTo(ImportRowStatus.PENDING);
        verify(rows, never()).findByImportIdAndStatus(anyLong(), any());
    }

    @Test
    void duplicatesEndpointGroupsBothDuplicateKinds() {
        CsvImportRow a = valid(1, -1000, "Coffee shop");
        CsvImportRow b = valid(2, -1000, "Coffee shop");
        a.markDuplicate(1L, null);
        b.markDuplicate(null, 555L);
        when(rows.findDuplicates(JOB_ID,
                List.of(ImportRowStatus.DUPLICATE, ImportRowStatus.VALID)))
                .thenReturn(List.of(a, b));

        List<DuplicateGroupResponse> groups = service.duplicates(USER, JOB_ID);

        assertThat(groups).hasSize(1);
        DuplicateGroupResponse group = groups.getFirst();
        assertThat(group.rows()).hasSize(2);
        assertThat(group.existingTransactionIds()).containsExactly(555L);
    }
}