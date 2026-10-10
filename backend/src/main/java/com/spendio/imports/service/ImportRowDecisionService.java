package com.spendio.imports.service;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.dto.request.RowDecisionRequest;
import com.spendio.imports.dto.request.UpdateImportRowRequest;
import com.spendio.imports.mapper.ImportMapper;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Row-level edits and bulk decisions (ARCHITECTURE.md D.12 step 7). */
@Service
public class ImportRowDecisionService {

    private final CsvImportService importService;
    private final CsvImportRowRepository rows;

    public ImportRowDecisionService(CsvImportService importService, CsvImportRowRepository rows) {
        this.importService = importService;
        this.rows = rows;
    }

    @Transactional(readOnly = true)
    public com.spendio.imports.dto.response.ImportRowPreviewResponse row(Long userId, Long importId, Long rowId) {
        importService.getOwned(userId, importId);
        CsvImportRow row = rows.findByIdAndImportId(rowId, importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_NOT_FOUND, "Import row not found")
                        .withProperty("rowId", rowId));
        return com.spendio.imports.mapper.ImportMapper.toRowPreview(row);
    }

    @Transactional
    public void updateRow(Long userId, Long importId, Long rowId, UpdateImportRowRequest request) {
        CsvImport job = importService.getOwned(userId, importId);
        CsvImportRow row = rows.findByIdAndImportId(rowId, importId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_NOT_FOUND, "Import row not found")
                        .withProperty("rowId", rowId));
        if (job.getStatus() == ImportStatus.COMPLETED || job.getStatus() == ImportStatus.COMPLETED_WITH_ERRORS
                || job.getStatus() == ImportStatus.FAILED || job.getStatus() == ImportStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.IMPORT_ROW_NOT_EDITABLE, "Import row is no longer editable");
        }
        row.applyEditedFields(request.occurredOn(), request.amountMinor(), request.currencyCode(),
                request.description(), request.accountId(), request.categoryId(), request.type(), request.notes());
        rows.save(row);
    }

    @Transactional
    public void applyDecisions(Long userId, Long importId, RowDecisionRequest request) {
        CsvImport job = importService.getOwned(userId, importId);
        if (job.getStatus() == ImportStatus.COMPLETED || job.getStatus() == ImportStatus.COMPLETED_WITH_ERRORS
                || job.getStatus() == ImportStatus.FAILED || job.getStatus() == ImportStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.IMPORT_ROW_NOT_EDITABLE, "Import row is no longer editable");
        }
        List<CsvImportRow> targets = rows.findByImportIdAndStatusInAndIdIn(importId,
                List.of(ImportRowStatus.DUPLICATE, ImportRowStatus.VALID), request.rowIds());
        for (var r : targets) {
            switch (request.decision()) {
                case "CONFIRM" -> r.markConfirmable();
                case "SKIP" -> r.markSkipped();
                case "UNCONFIRM" -> {
                    if (r.getDuplicateOfRowId() != null || r.getDuplicateOfTransactionId() != null) {
                        r.setStatusPreserveDuplicate();
                    } else {
                        r.setStatus(ImportRowStatus.VALID);
                    }
                }
                default -> {
                }
            }
            rows.save(r);
        }
    }
}