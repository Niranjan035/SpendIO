package com.spendio.imports.service;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.dto.response.DuplicateGroupResponse;
import com.spendio.imports.dto.response.DuplicateGroupResponse.DuplicateRow;
import com.spendio.imports.mapper.ImportMapper;
import com.spendio.imports.matcher.DescriptionNormalizer;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.imports.support.DedupeHashFactory;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.transactions.service.TransactionService;
import com.spendio.transactions.support.DedupeKeyFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Duplicate detection within-file and against ledger (ARCHITECTURE.md D.12 step 5). */
@Service
public class DuplicateDetectionService {

    private final CsvImportService importService;
    private final CsvImportRowRepository rows;
    private final TransactionService transactions;

    public DuplicateDetectionService(CsvImportService importService, CsvImportRowRepository rows,
            TransactionService transactions) {
        this.importService = importService;
        this.rows = rows;
        this.transactions = transactions;
    }

    @Transactional
    public void detect(Long userId, Long importId) {
        CsvImport job = importService.getOwned(userId, importId);
        if (job.getStatus() != ImportStatus.MAPPING_REQUIRED && job.getStatus() != ImportStatus.VALIDATING) {
            return;
        }
        List<CsvImportRow> valid = rows.findByImportIdAndStatus(job.getId(), ImportRowStatus.VALID);
        Map<String, List<CsvImportRow>> byHash = new LinkedHashMap<>();
        for (var r : valid) {
            String h = DedupeHashFactory.dedupeHash(r.getOccurredOn(), r.getAmountMinor(), r.getCurrencyCode(),
                    r.getDescription());
            byHash.computeIfAbsent(h, k -> new ArrayList<>()).add(r);
        }
        for (var e : byHash.entrySet()) {
            if (e.getValue().size() > 1) {
                Long firstId = e.getValue().get(0).getId();
                for (int i = 1; i < e.getValue().size(); i++) {
                    var row = e.getValue().get(i);
                    row.markDuplicate(firstId, null);
                    rows.save(row);
                }
            }
        }
        // ledger probe
        for (var r : valid) {
            String key = DedupeKeyFactory.contentKey(userId, r.getAccountId(), r.getOccurredOn(), r.getAmountMinor(),
                    r.getCurrencyCode(), r.getDescription());
            transactions.existingTransactionIdForDedupeKey(userId, key).ifPresent(existingId -> {
                r.markDuplicate(null, existingId);
                rows.save(r);
            });
        }
        job.markReady();
    }

    @Transactional(readOnly = true)
    public java.util.List<DuplicateGroupResponse> duplicates(Long userId, Long importId) {
        importService.getOwned(userId, importId);
        List<CsvImportRow> dups = rows.findDuplicates(importId,
                List.of(ImportRowStatus.DUPLICATE, ImportRowStatus.VALID));
        Map<String, List<CsvImportRow>> byHash = new LinkedHashMap<>();
        for (var r : dups) {
            String h = DedupeHashFactory.dedupeHash(r.getOccurredOn(), r.getAmountMinor(), r.getCurrencyCode(),
                    r.getDescription());
            byHash.computeIfAbsent(h, k -> new ArrayList<>()).add(r);
        }
        List<DuplicateGroupResponse> result = new ArrayList<>();
        for (var e : byHash.entrySet()) {
            List<DuplicateRow> drs = e.getValue().stream()
                    .map(r -> new DuplicateRow(r.getId(), r.getRowNumber(), r.getStatus().name(),
                            r.getOccurredOn(), r.getAmountMinor(), r.getCurrencyCode(), r.getDescription()))
                    .toList();
            List<Long> existing = e.getValue().stream()
                    .map(CsvImportRow::getDuplicateOfTransactionId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            result.add(new DuplicateGroupResponse(e.getKey(), drs, existing));
        }
        return result;
    }
}