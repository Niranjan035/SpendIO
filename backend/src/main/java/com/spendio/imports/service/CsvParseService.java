package com.spendio.imports.service;

import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.dto.response.ImportRowPreviewResponse;
import com.spendio.imports.dto.response.ImportSummaryResponse;
import com.spendio.imports.mapper.ImportMapper;
import com.spendio.imports.matcher.AmountParser;
import com.spendio.imports.matcher.DateParser;
import com.spendio.imports.matcher.DescriptionNormalizer;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.imports.storage.CsvStorage;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parses raw bytes into {@link CsvImportRow}s and marks PARSED/MAPPING_REQUIRED.
 */
@Service
public class CsvParseService {

    private final CsvStorage storage;
    private final CsvImportService importService;
    private final CsvImportRowRepository rows;
    private final AccountQueryService accounts;
    private final CategoryQueryService categories;

    public CsvParseService(CsvStorage storage, CsvImportService importService, CsvImportRowRepository rows,
            AccountQueryService accounts, CategoryQueryService categories) {
        this.storage = storage;
        this.importService = importService;
        this.rows = rows;
        this.accounts = accounts;
        this.categories = categories;
    }

    @Transactional
    public ImportSummaryResponse parse(Long userId, Long importId) {
        CsvImport job = importService.getOwned(userId, importId);
        if (job.getStatus() != ImportStatus.UPLOADED) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "Import is not ready for this action")
                    .withProperty("importId", importId)
                    .withProperty("status", job.getStatus());
        }
        byte[] bytes = storage.read(job.getStoredFileKey());
        String content = new String(bytes, StandardCharsets.UTF_8);
        com.spendio.imports.parser.CsvFormatDetector.CsvFormat format = com.spendio.imports.parser.CsvFormatDetector.detect(
                bytes, null, null);
        job.setDetectedDelimiterIfNull(format.delimiter() + "");
        job.setDetectedEncodingIfNull(format.encoding());
        var parsedRows = com.spendio.imports.parser.CsvStreamingReader.read(content, format.delimiter());
        int headerRow = 1;
        List<String> headers = List.of();
        int bodyStart = 0;
        if (!parsedRows.isEmpty()) {
            List<String> first = parsedRows.get(0).fields();
            if (com.spendio.imports.parser.CsvHeaderAnalyzer.looksLikeHeader(first)) {
                headers = com.spendio.imports.parser.CsvHeaderAnalyzer.cleanHeaders(first);
                headerRow = 1;
                bodyStart = 1;
            } else {
                headers = new ArrayList<>();
                for (int i = 0; i < first.size(); i++) {
                    headers.add("column_" + (i + 1));
                }
                bodyStart = 0;
            }
        }
        List<CsvImportRow> toSave = new ArrayList<>();
        for (int r = bodyStart; r < parsedRows.size() && toSave.size() < 50000; r++) {
            var row = parsedRows.get(r);
            Map<String, Object> raw = ImportMapper.rawFromHeaders(headers, row.fields());
            CsvImportRow ir = CsvImportRow.pending(job.getId(), r + 1, raw);
            toSave.add(ir);
        }
        rows.saveAll(toSave);
        job.markParsed(format.delimiter() + "", format.encoding(), headerRow, headers, toSave.size());
        job.requireMapping();
        return ImportMapper.toSummary(job);
    }

    @Transactional(readOnly = true)
    public com.spendio.sharedkernel.web.PageResponse<ImportRowPreviewResponse> previewPage(Long userId,
            Long importId, com.spendio.sharedkernel.domain.enums.ImportRowStatus status, Integer page,
            Integer size) {
        importService.getOwned(userId, importId);
        PageRequest pr = PageRequest.of(Math.max(page == null ? 0 : page, 0),
                Math.min(size == null ? 50 : size, 200),
                org.springframework.data.domain.Sort.by("rowNumber"));
        var result = status == null
                ? rows.findByImportId(importId, pr)
                : rows.findByImportIdAndStatus(importId, status, pr);
        return com.spendio.sharedkernel.web.PageResponse.from(
                result.map(ImportMapper::toRowPreview));
    }
}