package com.spendio.imports.service;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.dto.response.ImportReportResponse;
import com.spendio.imports.dto.response.ImportReportResponse.Failure;
import com.spendio.imports.dto.response.ImportReportResponse.TransactionLine;
import com.spendio.imports.dto.response.ImportRowPreviewResponse;
import com.spendio.imports.dto.response.ImportSummaryResponse;
import com.spendio.imports.mapper.ImportMapper;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.imports.support.CsvFormulaSanitizer;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.ImportCompletedEvent;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.transactions.service.TransactionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** History, reports, errors.csv and undo (ARCHITECTURE.md D.12 E.12). */
@Service
public class ImportHistoryService {

    private final CsvImportService importService;
    private final CsvImportRowRepository rows;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher events;

    public ImportHistoryService(CsvImportService importService, CsvImportRowRepository rows,
            TransactionService transactionService, ApplicationEventPublisher events) {
        this.importService = importService;
        this.rows = rows;
        this.transactionService = transactionService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportSummaryResponse> history(Long userId, ImportStatus status, Integer page,
            Integer size) {
        PageRequest pr = PageRequest.of(Math.max(page == null ? 0 : page, 0),
                Math.min(Math.max(size == null ? 20 : size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CsvImport> result = status == null
                ? importService.page(userId, pr)
                : importService.page(userId, status, pr);
        return PageResponse.from(result.map(ImportMapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ImportReportResponse report(Long userId, Long importId) {
        CsvImport job = importService.getOwned(userId, importId);
        List<CsvImportRow> all = rows.findByImportIdOrderByRowNumber(importId);
        List<TransactionLine> created = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (var row : all) {
            if (row.getStatus() == ImportRowStatus.CREATED && row.getCreatedTransactionId() != null) {
                created.add(new TransactionLine(row.getCreatedTransactionId(), row.getOccurredOn(),
                        Math.abs(row.getAmountMinor() == null ? 0L : row.getAmountMinor()),
                        row.getCurrencyCode(), row.getDescription()));
            } else if (row.getStatus() == ImportRowStatus.FAILED) {
                failures.add(new Failure(row.getRowNumber(), firstError(row),
                        row.getRawJson() == null ? Map.of() : row.getRawJson()));
            }
        }
        return new ImportReportResponse(ImportMapper.toSummary(job), created, failures);
    }

    /**
     * Downloads the failed rows as a sanitized CSV (formula injection guarded),
     * ARCHITECTURE.md D.12 E.12 {@code GET /imports/{id}/errors.csv}.
     */
    @Transactional(readOnly = true)
    public String errorsCsv(Long userId, Long importId) {
        CsvImport job = importService.getOwned(userId, importId);
        List<String> headers = job.getFileHeaders() == null ? List.of() : job.getFileHeaders();
        List<CsvImportRow> failed = rows.findByImportIdAndStatus(importId, ImportRowStatus.FAILED);
        StringBuilder sb = new StringBuilder();
        sb.append("rowNumber,error");
        for (String h : headers) {
            sb.append(',').append(escape(CsvFormulaSanitizer.sanitize(h)));
        }
        sb.append('\n');
        for (var row : failed) {
            sb.append(row.getRowNumber()).append(',').append(escape(CsvFormulaSanitizer.sanitize(firstError(row))));
            Map<String, Object> raw = row.getRawJson() == null ? Map.of() : row.getRawJson();
            for (String h : headers) {
                sb.append(',').append(escape(CsvFormulaSanitizer.sanitize(raw.get(h))));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Undo (ARCHITECTURE.md D.12 E.12): soft-deletes the ledger rows this import
     * created and moves the rows back to CONFIRMED so they can be re-imported.
     */
    @Transactional
    public int undo(Long userId, Long importId) {
        CsvImport job = importService.getOwned(userId, importId);
        if (job.getStatus() != ImportStatus.COMPLETED && job.getStatus() != ImportStatus.COMPLETED_WITH_ERRORS) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "Import is not ready for this action")
                    .withProperty("importId", importId)
                    .withProperty("status", job.getStatus());
        }
        List<CsvImportRow> created = rows.findByImportIdAndStatus(importId, ImportRowStatus.CREATED);
        List<Long> txIds = created.stream()
                .map(CsvImportRow::getCreatedTransactionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        transactionService.undoImportedTransactions(userId, txIds);
        for (var row : created) {
            row.markConfirmableFromUndone();
            rows.save(row);
        }
        return created.size();
    }

    private String firstError(CsvImportRow row) {
        return row.getErrors() == null || row.getErrors().isEmpty()
                ? "row failed"
                : row.getErrors().get(0);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}