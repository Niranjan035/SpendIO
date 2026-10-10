package com.spendio.imports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The saved column mapping (ARCHITECTURE.md D.12): the canonical field pointers are
 * separate columns for querying, while {@code columnMappings} keeps the full
 * {@code csvColumn -> {field, formatHint}} table as JSON.
 */
@Embeddable
public class ColumnMapping {

    @Column(name = "date_column")
    private String dateColumn;

    @Column(name = "amount_column")
    private String amountColumn;

    @Column(name = "description_column")
    private String descriptionColumn;

    @Column(name = "debit_column")
    private String debitColumn;

    @Column(name = "credit_column")
    private String creditColumn;

    @Column(name = "category_column")
    private String categoryColumn;

    @Column(name = "account_column")
    private String accountColumn;

    @Column(name = "currency_column")
    private String currencyColumn;

    @Column(name = "type_column")
    private String typeColumn;

    @Column(name = "date_format")
    private String dateFormat;

    @Column(name = "amount_format")
    private String amountFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping_json")
    private Map<String, Map<String, String>> columnMappings;

    protected ColumnMapping() {
    }

    public static ColumnMapping empty() {
        return new ColumnMapping();
    }

    public static ColumnMapping of(String dateColumn, String amountColumn, String descriptionColumn,
            String debitColumn, String creditColumn, String categoryColumn, String accountColumn,
            String currencyColumn, String typeColumn, String dateFormat, String amountFormat,
            Map<String, Map<String, String>> columnMappings) {
        ColumnMapping mapping = new ColumnMapping();
        mapping.dateColumn = blankToNull(dateColumn);
        mapping.amountColumn = blankToNull(amountColumn);
        mapping.descriptionColumn = blankToNull(descriptionColumn);
        mapping.debitColumn = blankToNull(debitColumn);
        mapping.creditColumn = blankToNull(creditColumn);
        mapping.categoryColumn = blankToNull(categoryColumn);
        mapping.accountColumn = blankToNull(accountColumn);
        mapping.currencyColumn = blankToNull(currencyColumn);
        mapping.typeColumn = blankToNull(typeColumn);
        mapping.dateFormat = blankToNull(dateFormat);
        mapping.amountFormat = blankToNull(amountFormat);
        mapping.columnMappings = columnMappings == null || columnMappings.isEmpty()
                ? null
                : new LinkedHashMap<>(columnMappings);
        return mapping;
    }

    public String getDateColumn() {
        return dateColumn;
    }

    public String getAmountColumn() {
        return amountColumn;
    }

    public String getDescriptionColumn() {
        return descriptionColumn;
    }

    public String getDebitColumn() {
        return debitColumn;
    }

    public String getCreditColumn() {
        return creditColumn;
    }

    public String getCategoryColumn() {
        return categoryColumn;
    }

    public String getAccountColumn() {
        return accountColumn;
    }

    public String getCurrencyColumn() {
        return currencyColumn;
    }

    public String getTypeColumn() {
        return typeColumn;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public String getAmountFormat() {
        return amountFormat;
    }

    public Map<String, Map<String, String>> getColumnMappings() {
        return columnMappings;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}