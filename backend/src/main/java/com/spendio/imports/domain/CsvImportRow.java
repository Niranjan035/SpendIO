package com.spendio.imports.domain;

import com.spendio.sharedkernel.domain.AuditableEntity;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One source row of an import (ARCHITECTURE.md D.12). {@code rawJson} is the verbatim
 * CSV record, {@code parsedJson} the normalised field values; the typed columns on the
 * side keep preview/execution queries indexable. Row status advances
 * PENDING -> VALID | INVALID | DUPLICATE -> CONFIRMED -> CREATED | SKIPPED | FAILED.
 */
@Entity
@Table(name = "csv_import_rows")
public class CsvImportRow extends AuditableEntity {

    @Column(name = "import_id", nullable = false)
    private Long importId;

    /** Row position within the import; backticks because {@code row_number} is a MySQL 8 keyword. */
    @Column(name = "`row_number`", nullable = false)
    private int rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImportRowStatus status = ImportRowStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false)
    private Map<String, Object> rawJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json")
    private Map<String, Object> parsedJson;

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "description")
    private String description;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "type")
    private String type;

    @Column(name = "notes")
    private String notes;

    @Column(name = "matched_account_id")
    private Long matchedAccountId;

    @Column(name = "matched_category_id")
    private Long matchedCategoryId;

    @Column(name = "matched_by")
    private String matchedBy;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "duplicate_of_row_id")
    private Long duplicateOfRowId;

    @Column(name = "duplicate_of_transaction_id")
    private Long duplicateOfTransactionId;

    @Column(name = "created_transaction_id")
    private Long createdTransactionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors_json")
    private List<String> errors;

    protected CsvImportRow() {
    }

    public static CsvImportRow pending(Long importId, int rowNumber, Map<String, Object> raw) {
        CsvImportRow row = new CsvImportRow();
        row.importId = importId;
        row.rowNumber = rowNumber;
        row.rawJson = new LinkedHashMap<>(raw);
        row.status = ImportRowStatus.PENDING;
        return row;
    }

    public void storeParsed(Map<String, Object> parsed, LocalDate occurredOn, Long amountMinor,
            String currencyCode, String description, String type, List<String> errors) {
        this.parsedJson = new LinkedHashMap<>(parsed);
        this.occurredOn = occurredOn;
        this.amountMinor = amountMinor;
        this.currencyCode = currencyCode;
        this.description = trimNullOr(description);
        this.type = type;
        this.errors = errors == null || errors.isEmpty() ? null : errors;
        this.status = (errors == null || errors.isEmpty()) ? ImportRowStatus.VALID : ImportRowStatus.INVALID;
    }

    public void markDuplicate(Long duplicateOfRowId, Long duplicateOfTransactionId) {
        this.duplicateOfRowId = duplicateOfRowId;
        this.duplicateOfTransactionId = duplicateOfTransactionId;
        this.status = ImportRowStatus.DUPLICATE;
    }

    public void markConfirmable() {
        if (this.status == ImportRowStatus.DUPLICATE || this.status == ImportRowStatus.VALID) {
            this.status = ImportRowStatus.CONFIRMED;
        }
    }

    public void markSkipped() {
        this.status = ImportRowStatus.SKIPPED;
    }

    public void markCreated(Long transactionId) {
        this.createdTransactionId = transactionId;
        this.status = ImportRowStatus.CREATED;
    }

    public void markFailed(List<String> rowErrors) {
        this.errors = rowErrors == null || rowErrors.isEmpty() ? List.of("execution failed") : rowErrors;
        this.status = ImportRowStatus.FAILED;
    }

    public void markUndone() {
        this.status = ImportRowStatus.UNDONE;
    }

    public void setStatus(ImportRowStatus status) {
        this.status = status;
    }

    public void setStatusPreserveDuplicate() {
        if (this.duplicateOfRowId != null || this.duplicateOfTransactionId != null) {
            this.status = ImportRowStatus.DUPLICATE;
        } else {
            this.status = ImportRowStatus.VALID;
        }
    }

    /** Undo: rows go back to CONFIRMED so re-import is possible (D.12 E.12). */
    public void markConfirmableFromUndone() {
        this.createdTransactionId = null;
        this.status = ImportRowStatus.CONFIRMED;
    }

    public void applyEditedFields(LocalDate occurredOn, Long amountMinor, String currencyCode,
            String description, Long accountId, Long categoryId, String type, String notes) {
        this.occurredOn = occurredOn;
        this.amountMinor = amountMinor;
        this.currencyCode = currencyCode;
        this.description = trimNullOr(description);
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.type = type;
        this.notes = trimNullOr(notes);
        this.errors = null;
        this.status = ImportRowStatus.VALID;
    }

    public void setMatchedAccount(Long matchedAccountId, String matchedBy, BigDecimal confidence) {
        this.matchedAccountId = matchedAccountId;
        this.matchedBy = matchedBy;
        this.confidence = confidence;
    }

    public void setMatchedCategory(Long matchedCategoryId, String matchedBy, BigDecimal confidence) {
        this.matchedCategoryId = matchedCategoryId;
        this.matchedBy = matchedBy;
        this.confidence = confidence;
    }

    public Long getImportId() {
        return importId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public ImportRowStatus getStatus() {
        return status;
    }

    public Map<String, Object> getRawJson() {
        return rawJson;
    }

    public Map<String, Object> getParsedJson() {
        return parsedJson;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getDescription() {
        return description;
    }

    public Long getAccountId() {
        return accountId != null ? accountId : matchedAccountId;
    }

    public Long getCategoryId() {
        return categoryId != null ? categoryId : matchedCategoryId;
    }

    public String getType() {
        return type;
    }

    public String getNotes() {
        return notes;
    }

    public Long getMatchedAccountId() {
        return matchedAccountId;
    }

    public Long getMatchedCategoryId() {
        return matchedCategoryId;
    }

    public String getMatchedBy() {
        return matchedBy;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public Long getDuplicateOfRowId() {
        return duplicateOfRowId;
    }

    public Long getDuplicateOfTransactionId() {
        return duplicateOfTransactionId;
    }

    public Long getCreatedTransactionId() {
        return createdTransactionId;
    }

    public List<String> getErrors() {
        return errors;
    }

    @jakarta.persistence.Transient
    public String getDedupeHashForCompute() {
        return com.spendio.imports.support.DedupeHashFactory.dedupeHash(
                occurredOn, amountMinor, currencyCode, description);
    }

    private static String trimNullOr(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}