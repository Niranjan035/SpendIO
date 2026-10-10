package com.spendio.imports.service;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.domain.ImportOptions;
import com.spendio.imports.dto.request.ExecuteImportRequest;
import com.spendio.imports.dto.response.ImportReportResponse;
import com.spendio.imports.dto.response.ImportReportResponse.Failure;
import com.spendio.imports.dto.response.ImportReportResponse.TransactionLine;
import com.spendio.imports.dto.response.ImportSummaryResponse;
import com.spendio.imports.mapper.ImportMapper;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.ImportCompletedEvent;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.service.TransactionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Execution engine (ARCHITECTURE.md D.12 step 8): every row posts in its own
 * REQUIRES_NEW transaction so one bad row never rolls back the good ones, partial
 * import honours {@code allowPartialImport}, and re-running is a no-op for rows already
 * created.
 */
@Service
public class ImportExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ImportExecutionService.class);

    private final CsvImportService importService;
    private final CsvImportRowRepository rows;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate template;

    public ImportExecutionService(CsvImportService importService, CsvImportRowRepository rows,
            TransactionService transactionService,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.importService = importService;
        this.rows = rows;
        this.transactionService = transactionService;
        this.events = events;
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public ImportReportResponse execute(Long userId, Long importId, ExecuteImportRequest request) {
        CsvImport job = importService.getOwned(userId, importId);
        ImportStatus status = job.getStatus();
        if (status == ImportStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "Import is not ready for this action")
                    .withProperty("importId", importId)
                    .withProperty("status", status);
        }
        if (status != ImportStatus.READY && status != ImportStatus.COMPLETED_WITH_ERRORS) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "Import is not ready for this action")
                    .withProperty("importId", importId)
                    .withProperty("status", status);
        }
        ImportOptions options = job.getOptions();
        boolean allowPartial = request.allowPartialImport() == null
                ? options.allowPartialImport()
                : request.allowPartialImport();
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());
        List<CsvImportRow> targets = targetRows(job, request);
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "No rows are ready to import")
                    .withProperty("importId", importId);
        }
        if (!dryRun) {
            job.markProcessing();
        }

        List<TransactionLine> created = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        List<Long> createdTxIds = new ArrayList<>();
        int failed = 0;
        int skipped = 0;

        for (var row : targets) {
            if (dryRun) {
                failures.add(new Failure(row.getRowNumber(), describeSkipped(row),
                        row.getRawJson() == null ? Map.of() : row.getRawJson()));
                continue;
            }
            try {
                Long txId = template.execute(txStatus -> postRow(userId, row, job.getId()));
                row.markCreated(txId);
                rows.save(row);
                createdTxIds.add(txId);
                created.add(line(row, txId));
            } catch (RuntimeException rowFailed) {
                failed++;
                row.markFailed(List.of(message(rowFailed)));
                rows.save(row);
                failures.add(new Failure(row.getRowNumber(), message(rowFailed),
                        row.getRawJson() == null ? Map.of() : row.getRawJson()));
                log.warn("Import {} row {} failed: {}", importId, row.getRowNumber(), rowFailed.getMessage());
                if (!allowPartial) {
                    // every row posts in its own committed transaction, so an abort has
                    // to undo the rows posted so far - all-or-nothing semantics
                    undoCreated(userId, createdTxIds);
                    throw new BusinessException(ErrorCode.IMPORT_MAPPING_INVALID,
                            "Import aborted: row " + row.getRowNumber() + " failed and partial import is disabled")
                            .withProperty("importId", importId)
                            .withProperty("rowNumber", row.getRowNumber());
                }
            }
        }
        if (!dryRun) {
            job.addCreated(created.size(), skipped, failed);
            job.finish(failed > 0);
            events.publishEvent(new ImportCompletedEvent(userId, importId, job.getOriginalFilename(),
                    job.getTotalRows(), job.getCreatedRows(), job.getFailedRows(), job.getCompletedAt(),
                    ImportCompletedEvent.dedupeKey(importId)));
        }
        ImportSummaryResponse summary = ImportMapper.toSummary(job);
        return new ImportReportResponse(summary, created, failures);
    }

    private List<CsvImportRow> targetRows(CsvImport job, ExecuteImportRequest request) {
        Long importId = job.getId();
        if (request.rowIds() != null && !request.rowIds().isEmpty()) {
            return rows.findByImportIdAndStatusInAndIdIn(importId,
                    List.of(ImportRowStatus.CONFIRMED, ImportRowStatus.VALID, ImportRowStatus.DUPLICATE),
                    request.rowIds());
        }
        if (job.getOptions().skipDuplicates()) {
            return rows.findByImportIdAndStatusIn(importId,
                    List.of(ImportRowStatus.CONFIRMED, ImportRowStatus.VALID));
        }
        return rows.findByImportIdAndStatusIn(importId,
                List.of(ImportRowStatus.CONFIRMED, ImportRowStatus.VALID, ImportRowStatus.DUPLICATE));
    }

    private Long postRow(Long userId, CsvImportRow row, Long importId) {
        CreateTransactionRequest request = new CreateTransactionRequest(
                row.getAccountId(),
                row.getCategoryId(),
                TransactionType.valueOf(row.getType()),
                null,
                Math.abs(row.getAmountMinor() == null ? 0L : row.getAmountMinor()),
                row.getCurrencyCode(),
                null,
                row.getOccurredOn(),
                row.getDescription(),
                null,
                null,
                row.getNotes(),
                null,
                null);
        String idempotencyKey = "import:" + importId + ":" + row.getRowNumber();
        return transactionService.createForCsvImport(userId, request, importId, row.getId(), idempotencyKey).id();
    }

    private TransactionLine line(CsvImportRow row, Long txId) {
        return new TransactionLine(txId, row.getOccurredOn(),
                Math.abs(row.getAmountMinor() == null ? 0L : row.getAmountMinor()),
                row.getCurrencyCode(), row.getDescription());
    }

    /** Soft-deletes rows already committed in their own tx when an abort fires. */
    private void undoCreated(Long userId, List<Long> txIds) {
        if (txIds.isEmpty()) {
            return;
        }
        template.execute(txStatus -> {
            transactionService.undoImportedTransactions(userId, txIds);
            return null;
        });
    }

    private String describeSkipped(CsvImportRow row) {
        if (row.getStatus() == ImportRowStatus.DUPLICATE) {
            return "dry-run: row " + row.getRowNumber() + " is a duplicate";
        }
        return "dry-run: row " + row.getRowNumber() + " would be imported";
    }

    private String message(Throwable t) {
        if (t instanceof com.spendio.sharedkernel.error.BusinessException be && be.getMessage() != null) {
            return be.getMessage();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}