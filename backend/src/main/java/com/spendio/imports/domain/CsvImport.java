package com.spendio.imports.domain;

import com.spendio.sharedkernel.domain.AuditableEntity;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A CSV import job (ARCHITECTURE.md D.12): the status state machine is explicit and
 * every stage advances the running totals so the UI can poll progress.
 */
@Entity
@Table(name = "csv_imports")
public class CsvImport extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportStatus status = ImportStatus.UPLOADED;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "stored_file_key", nullable = false)
    private String storedFileKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "detected_delimiter")
    private String detectedDelimiter;

    @Column(name = "detected_encoding")
    private String detectedEncoding;

    @Column(name = "header_row_number")
    private Integer headerRowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_headers")
    private java.util.List<String> fileHeaders;

    @Embedded
    private ColumnMapping mapping;

    @Embedded
    private ImportOptions options;

    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "valid_rows")
    private int validRows;

    @Column(name = "invalid_rows")
    private int invalidRows;

    @Column(name = "duplicate_rows")
    private int duplicateRows;

    @Column(name = "created_rows")
    private int createdRows;

    @Column(name = "skipped_rows")
    private int skippedRows;

    @Column(name = "failed_rows")
    private int failedRows;

    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CsvImport() {
    }

    public static CsvImport uploaded(Long userId, String originalFilename, long fileSizeBytes,
            String storedFileKey, String checksum, ImportOptions options) {
        CsvImport job = new CsvImport();
        job.userId = userId;
        job.originalFilename = originalFilename;
        job.fileSizeBytes = fileSizeBytes;
        job.storedFileKey = storedFileKey;
        job.checksum = checksum;
        job.options = options;
        job.mapping = ColumnMapping.empty();
        job.status = ImportStatus.UPLOADED;
        return job;
    }

    public void markParsing() {
        this.status = ImportStatus.PARSING;
    }

    public void markParsed(String delimiter, String encoding, Integer headerRowNumber,
            java.util.List<String> headers, int rowCount) {
        if (this.detectedDelimiter == null) {
            this.detectedDelimiter = delimiter;
        }
        if (this.detectedEncoding == null) {
            this.detectedEncoding = encoding;
        }
        this.headerRowNumber = headerRowNumber;
        this.fileHeaders = headers;
        this.totalRows = rowCount;
        this.status = ImportStatus.PARSED;
    }

    public void setDetectedDelimiterIfNull(String delimiter) {
        if (this.detectedDelimiter == null && delimiter != null && !delimiter.isBlank()) {
            this.detectedDelimiter = delimiter;
        }
    }

    public void setDetectedEncodingIfNull(String encoding) {
        if (this.detectedEncoding == null && encoding != null && !encoding.isBlank()) {
            this.detectedEncoding = encoding;
        }
    }

    public void requireMapping() {
        this.status = ImportStatus.MAPPING_REQUIRED;
    }

    public void markValidating() {
        this.status = ImportStatus.VALIDATING;
    }

    public void markReady() {
        this.status = ImportStatus.READY;
    }

    public void markProcessing() {
        this.status = ImportStatus.PROCESSING;
        this.startedAt = Instant.now();
    }

    public void finish(boolean withErrors) {
        this.status = withErrors ? ImportStatus.COMPLETED_WITH_ERRORS : ImportStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed(String summary) {
        this.status = ImportStatus.FAILED;
        this.errorSummary = summary;
        this.completedAt = Instant.now();
    }

    public void saveMapping(ColumnMapping mapping, ImportOptions options) {
        this.mapping = mapping;
        this.options = options;
        this.status = ImportStatus.MAPPING_REQUIRED;
    }

    public void setTotals(int valid, int invalid, int duplicates) {
        this.validRows = valid;
        this.invalidRows = invalid;
        this.duplicateRows = duplicates;
    }

    public void addCreated(long created, long skipped, long failed) {
        this.createdRows += created;
        this.skippedRows += skipped;
        this.failedRows += failed;
    }

    public void resetTotalsForRevalidate() {
        this.validRows = 0;
        this.invalidRows = 0;
        this.duplicateRows = 0;
    }

    public Long getUserId() {
        return userId;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getStoredFileKey() {
        return storedFileKey;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getDetectedDelimiter() {
        return detectedDelimiter;
    }

    public String getDetectedEncoding() {
        return detectedEncoding;
    }

    public Integer getHeaderRowNumber() {
        return headerRowNumber;
    }

    public java.util.List<String> getFileHeaders() {
        return fileHeaders;
    }

    public ColumnMapping getMapping() {
        return mapping == null ? ColumnMapping.empty() : mapping;
    }

    public ImportOptions getOptions() {
        return options == null ? ImportOptions.empty() : options;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public int getCreatedRows() {
        return createdRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /** Convenience: the account rows default to when none is matched but one is set. */
    public Long resolveTargetAccountId() {
        ImportOptions opts = getOptions();
        if (opts.targetAccountId() != null) {
            return opts.targetAccountId();
        }
        return null;
    }

    public boolean completed() {
        return status == ImportStatus.COMPLETED || status == ImportStatus.COMPLETED_WITH_ERRORS
                || status == ImportStatus.FAILED;
    }
}