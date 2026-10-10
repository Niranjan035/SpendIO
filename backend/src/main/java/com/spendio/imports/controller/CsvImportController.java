package com.spendio.imports.controller;

import com.spendio.imports.dto.request.CreateImportRequest;
import com.spendio.imports.dto.request.ExecuteImportRequest;
import com.spendio.imports.dto.request.RowDecisionRequest;
import com.spendio.imports.dto.request.SaveMappingRequest;
import com.spendio.imports.dto.request.UpdateImportRowRequest;
import com.spendio.imports.dto.response.ColumnMappingResponse;
import com.spendio.imports.dto.response.DuplicateGroupResponse;
import com.spendio.imports.dto.response.ImportReportResponse;
import com.spendio.imports.dto.response.ImportRowPreviewResponse;
import com.spendio.imports.dto.response.ImportSummaryResponse;
import com.spendio.imports.service.ColumnMappingService;
import com.spendio.imports.service.CsvImportService;
import com.spendio.imports.service.CsvParseService;
import com.spendio.imports.service.CsvRowNormalizationService;
import com.spendio.imports.service.DuplicateDetectionService;
import com.spendio.imports.service.ImportExecutionService;
import com.spendio.imports.service.ImportHistoryService;
import com.spendio.imports.service.ImportRowDecisionService;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV import endpoints (ARCHITECTURE.md E.12): upload -> columns -> mapping ->
 * validate -> preview -> decisions -> execute -> report.
 */
@RestController
@RequestMapping("/api/v1/imports")
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final CsvParseService parseService;
    private final ColumnMappingService mappingService;
    private final CsvRowNormalizationService normalizationService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final ImportRowDecisionService decisionService;
    private final ImportExecutionService executionService;
    private final ImportHistoryService historyService;

    public CsvImportController(CsvImportService csvImportService, CsvParseService parseService,
            ColumnMappingService mappingService, CsvRowNormalizationService normalizationService,
            DuplicateDetectionService duplicateDetectionService, ImportRowDecisionService decisionService,
            ImportExecutionService executionService, ImportHistoryService historyService) {
        this.csvImportService = csvImportService;
        this.parseService = parseService;
        this.mappingService = mappingService;
        this.normalizationService = normalizationService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.decisionService = decisionService;
        this.executionService = executionService;
        this.historyService = historyService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportSummaryResponse upload(@CurrentUserId Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "defaultCurrencyCode", required = false) String defaultCurrencyCode,
            @RequestParam(value = "defaultDateFormat", required = false) String defaultDateFormat,
            @RequestParam(value = "delimiterOverride", required = false) String delimiterOverride,
            @RequestParam(value = "encodingOverride", required = false) String encodingOverride,
            @RequestParam(value = "headerRowNumber", required = false) Integer headerRowNumber,
            @RequestParam(value = "targetAccountId", required = false) Long targetAccountId) {
        CreateImportRequest request = new CreateImportRequest(defaultCurrencyCode, defaultDateFormat,
                delimiterOverride, encodingOverride, headerRowNumber, targetAccountId);
        ImportSummaryResponse job = csvImportService.upload(userId, file, request);
        return parseService.parse(userId, job.id());
    }

    @GetMapping
    public PageResponse<ImportSummaryResponse> history(@CurrentUserId Long userId,
            @RequestParam(value = "status", required = false) ImportStatus status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return historyService.history(userId, status, page, size);
    }

    @GetMapping("/{id}")
    public ImportSummaryResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return csvImportService.summary(userId, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        csvImportService.delete(userId, id);
    }

    @GetMapping("/{id}/columns")
    public ColumnMappingResponse columns(@CurrentUserId Long userId, @PathVariable Long id) {
        return mappingService.suggest(userId, id);
    }

    @PutMapping("/{id}/mapping")
    public ImportSummaryResponse saveMapping(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody SaveMappingRequest request) {
        mappingService.saveMapping(userId, id, request);
        normalizationService.normalize(userId, id);
        duplicateDetectionService.detect(userId, id);
        return csvImportService.summary(userId, id);
    }

    @PostMapping("/{id}/validate")
    public ImportSummaryResponse validate(@CurrentUserId Long userId, @PathVariable Long id) {
        normalizationService.normalize(userId, id);
        duplicateDetectionService.detect(userId, id);
        return csvImportService.summary(userId, id);
    }

    @GetMapping("/{id}/preview")
    public PageResponse<ImportRowPreviewResponse> preview(@CurrentUserId Long userId, @PathVariable Long id,
            @RequestParam(value = "status", required = false) ImportRowStatus status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return parseService.previewPage(userId, id, status, page, size);
    }

    @GetMapping("/{id}/duplicates")
    public List<DuplicateGroupResponse> duplicates(@CurrentUserId Long userId, @PathVariable Long id) {
        return duplicateDetectionService.duplicates(userId, id);
    }

    @PatchMapping("/{id}/rows/{rowId}")
    public ImportRowPreviewResponse updateRow(@CurrentUserId Long userId, @PathVariable Long id,
            @PathVariable Long rowId, @Valid @RequestBody UpdateImportRowRequest request) {
        decisionService.updateRow(userId, id, rowId, request);
        return decisionService.row(userId, id, rowId);
    }

    @PostMapping("/{id}/rows/decisions")
    public void decisions(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody RowDecisionRequest request) {
        decisionService.applyDecisions(userId, id, request);
    }

    @PostMapping("/{id}/execute")
    public ImportReportResponse execute(@CurrentUserId Long userId, @PathVariable Long id,
            @RequestBody(required = false) ExecuteImportRequest request) {
        return executionService.execute(userId, id,
                request == null ? new ExecuteImportRequest(null, null, null) : request);
    }

    @GetMapping("/{id}/report")
    public ImportReportResponse report(@CurrentUserId Long userId, @PathVariable Long id) {
        return historyService.report(userId, id);
    }

    @GetMapping("/{id}/errors.csv")
    public ResponseEntity<byte[]> errorsCsv(@CurrentUserId Long userId, @PathVariable Long id) {
        String csv = historyService.errorsCsv(userId, id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("import-" + id + "-errors.csv", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{id}/undo")
    public Map<String, Integer> undo(@CurrentUserId Long userId, @PathVariable Long id) {
        int undone = historyService.undo(userId, id);
        return Map.of("undone", undone);
    }
}
