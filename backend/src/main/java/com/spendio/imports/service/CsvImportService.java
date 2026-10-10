package com.spendio.imports.service;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.ImportOptions;
import com.spendio.imports.dto.request.CreateImportRequest;
import com.spendio.imports.dto.response.ImportSummaryResponse;
import com.spendio.imports.mapper.ImportMapper;
import com.spendio.imports.repository.CsvImportRepository;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.imports.storage.CsvStorage;
import com.spendio.imports.storage.StoredFile;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV import job lifecycle (ARCHITECTURE.md D.12): upload -> persist job -> store file.
 */
@Service
public class CsvImportService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final CsvStorage storage;
    private final CsvImportRepository imports;
    private final CsvImportRowRepository rows;

    public CsvImportService(CsvStorage storage, CsvImportRepository imports, CsvImportRowRepository rows) {
        this.storage = storage;
        this.imports = imports;
        this.rows = rows;
    }

    @Transactional
    public ImportSummaryResponse upload(Long userId, MultipartFile file, CreateImportRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.IMPORT_UNSUPPORTED_FORMAT, "No file uploaded");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMPORT_FILE_TOO_LARGE, "File exceeds 5MB")
                    .withProperty("maxBytes", MAX_BYTES)
                    .withProperty("size", file.getSize());
        }
        String original = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".csv") && !original.toLowerCase().endsWith(".txt")) {
            throw new BusinessException(ErrorCode.IMPORT_UNSUPPORTED_FORMAT, "Unsupported file format")
                    .withProperty("filename", original);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot read uploaded file", e);
        }
        String checksum = sha256(bytes);
        StoredFile stored = storage.store(bytes);
        ImportOptions options = ImportOptions.of(
                request.defaultCurrencyCode(),
                request.defaultDateFormat(),
                request.targetAccountId(),
                false,
                true,
                true,
                null);
        CsvImport job = CsvImport.uploaded(userId, original, stored.sizeBytes(), stored.key(), checksum, options);
        imports.save(job);
        return ImportMapper.toSummary(job);
    }

    @Transactional(readOnly = true)
    public CsvImport getOwned(Long userId, Long importId) {
        return imports.findByIdAndUserId(importId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMPORT_NOT_FOUND, "Import not found")
                        .withProperty("importId", importId));
    }

    @Transactional(readOnly = true)
    public ImportSummaryResponse summary(Long userId, Long importId) {
        return ImportMapper.toSummary(getOwned(userId, importId));
    }

    @Transactional(readOnly = true)
    public List<ImportSummaryResponse> history(Long userId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return imports.findByUserId(userId, pageable).getContent().stream()
                .map(ImportMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CsvImport> page(Long userId,
            org.springframework.data.domain.Pageable pageable) {
        return imports.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CsvImport> page(Long userId,
            com.spendio.sharedkernel.domain.enums.ImportStatus status,
            org.springframework.data.domain.Pageable pageable) {
        return imports.findByUserIdAndStatus(userId, status, pageable);
    }

    @Transactional
    public void delete(Long userId, Long importId) {
        CsvImport job = getOwned(userId, importId);
        if (job.completed()) {
            throw new BusinessException(ErrorCode.IMPORT_ALREADY_EXECUTED, "Import already executed")
                    .withProperty("importId", importId);
        }
        rows.findByImportIdOrderByRowNumber(importId).forEach(rows::delete);
        imports.delete(job);
        storage.delete(job.getStoredFileKey());
    }

    @Transactional
    public void cancel(Long userId, Long importId) {
        CsvImport job = getOwned(userId, importId);
        if (job.completed()) {
            return;
        }
        job.markFailed("cancelled");
        imports.save(job);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 required", impossible);
        }
    }
}