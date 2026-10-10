package com.spendio.imports.service;

import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.dto.request.SaveMappingRequest;
import com.spendio.imports.dto.response.ColumnMappingResponse;
import com.spendio.imports.dto.response.ImportSummaryResponse;
import com.spendio.imports.mapper.ImportMapper;
import com.spendio.imports.matcher.AmountParser;
import com.spendio.imports.matcher.DateParser;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Column mapping suggestions and saving (ARCHITECTURE.md D.12 step 3). */
@Service
public class ColumnMappingService {

    private final CsvImportService importService;
    private final CsvImportRowRepository rows;
    private final AccountQueryService accounts;
    private final CategoryQueryService categories;

    private static final Map<String, String> FIELD_CANDIDATES = Map.ofEntries(
            Map.entry("date", "date"),
            Map.entry("occurred", "date"),
            Map.entry("transaction", "date"),
            Map.entry("posting", "date"),
            Map.entry("amount", "amount"),
            Map.entry("total", "amount"),
            Map.entry("debit", "debit"),
            Map.entry("credit", "credit"),
            Map.entry("description", "description"),
            Map.entry("desc", "description"),
            Map.entry("payee", "description"),
            Map.entry("merchant", "description"),
            Map.entry("category", "category"),
            Map.entry("account", "account"),
            Map.entry("currency", "currency"),
            Map.entry("type", "type"));

    public ColumnMappingService(CsvImportService importService, CsvImportRowRepository rows,
            AccountQueryService accounts, CategoryQueryService categories) {
        this.importService = importService;
        this.rows = rows;
        this.accounts = accounts;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public ColumnMappingResponse suggest(Long userId, Long importId) {
        CsvImport job = importService.getOwned(userId, importId);
        if (job.getStatus() != ImportStatus.PARSED && job.getStatus() != ImportStatus.MAPPING_REQUIRED) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "Import is not ready for this action")
                    .withProperty("importId", importId)
                    .withProperty("status", job.getStatus());
        }
        List<String> headers = job.getFileHeaders() == null ? List.of() : job.getFileHeaders();
        List<ColumnMappingResponse.Column> columns = new ArrayList<>();
        var sampleRows = rows.findByImportId(job.getId(), PageRequest.of(0, 5)).getContent();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            List<String> samples = new ArrayList<>();
            for (var sr : sampleRows) {
                Map<String, Object> raw = sr.getRawJson();
                if (raw != null && raw.containsKey(header)) {
                    Object v = raw.get(header);
                    if (v != null) {
                        samples.add(String.valueOf(v));
                    }
                } else if (raw != null && raw.size() > i) {
                    Object v = raw.values().toArray()[i];
                    if (v != null) {
                        samples.add(String.valueOf(v));
                    }
                }
            }
            String suggested = inferField(header, samples);
            double confidence = suggested == null ? 0.0 : 0.9;
            columns.add(new ColumnMappingResponse.Column(i, header, samples, suggested, confidence));
        }
        List<ColumnMappingResponse.FieldPointer> mapping = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        Set<String> required = Set.of("date", "amount");
        Set<String> mapped = new HashSet<>();
        for (var c : columns) {
            if (c.suggestedField() != null && required.contains(c.suggestedField())) {
                mapping.add(new ColumnMappingResponse.FieldPointer(c.suggestedField(), c.headerName(), null));
                mapped.add(c.suggestedField());
            }
        }
        for (String r : required) {
            if (!mapped.contains(r)) {
                unmapped.add(r);
            }
        }
        return new ColumnMappingResponse(columns, mapping, unmapped);
    }

    @Transactional
    public ImportSummaryResponse saveMapping(Long userId, Long importId, SaveMappingRequest request) {
        CsvImport job = importService.getOwned(userId, importId);
        if (job.getStatus() != ImportStatus.PARSED && job.getStatus() != ImportStatus.MAPPING_REQUIRED) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "Import is not ready for this action")
                    .withProperty("importId", importId)
                    .withProperty("status", job.getStatus());
        }
        Map<String, Map<String, String>> columnMappings = new HashMap<>();
        for (var e : request.columnMappings().entrySet()) {
            Map<String, String> m = new HashMap<>();
            m.put("field", e.getValue().field());
            if (e.getValue().formatHint() != null) {
                m.put("formatHint", e.getValue().formatHint());
            }
            columnMappings.put(e.getKey(), m);
        }
        var cm = com.spendio.imports.domain.ColumnMapping.of(
                request.dateColumn(),
                request.amountColumn(),
                request.descriptionColumn(),
                request.debitColumn(),
                request.creditColumn(),
                request.categoryColumn(),
                request.accountColumn(),
                request.currencyColumn(),
                request.typeColumn(),
                request.dateFormat(),
                request.amountFormat(),
                columnMappings);
        var opts = com.spendio.imports.domain.ImportOptions.of(
                request.options() == null ? null : request.options().defaultCurrencyCode(),
                request.options() == null ? null : request.options().defaultDateFormat(),
                request.options() == null ? null : request.options().targetAccountId(),
                request.options() == null ? false : Boolean.TRUE.equals(request.options().createMissingCategories()),
                request.options() == null ? true : Boolean.TRUE.equals(request.options().skipDuplicates()),
                request.options() == null ? true : Boolean.TRUE.equals(request.options().allowPartialImport()),
                request.options() == null ? null : request.options().defaultType());
        job.saveMapping(cm, opts);
        return ImportMapper.toSummary(job);
    }

    private String inferField(String header, List<String> samples) {
        String h = header == null ? "" : header.toLowerCase();
        for (var e : FIELD_CANDIDATES.entrySet()) {
            if (h.contains(e.getKey())) {
                return e.getValue();
            }
        }
        for (String s : samples) {
            OptionalLong minor = AmountParser.parseMinor(s);
            if (minor.isPresent()) {
                return "amount";
            }
            if (DateParser.tryParse(s, null).isPresent()) {
                return "date";
            }
        }
        return null;
    }
}