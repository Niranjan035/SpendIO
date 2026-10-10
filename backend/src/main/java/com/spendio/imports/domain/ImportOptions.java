package com.spendio.imports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Import options (ARCHITECTURE.md D.12): account/currency/date defaults plus the
 * behaviour switches - category auto-create, duplicate skipping, partial success.
 */
@Embeddable
public class ImportOptions {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency_code", length = 3)
    private String defaultCurrencyCode;

    @Column(name = "default_date_format")
    private String defaultDateFormat;

    @Column(name = "target_account_id")
    private Long targetAccountId;

    @Column(name = "create_missing_categories", nullable = false)
    private boolean createMissingCategories;

    @Column(name = "skip_duplicates", nullable = false)
    private boolean skipDuplicates = true;

    @Column(name = "allow_partial_import", nullable = false)
    private boolean allowPartialImport = true;

    @Column(name = "default_type")
    private String defaultType;

    protected ImportOptions() {
    }

    public static ImportOptions empty() {
        ImportOptions options = new ImportOptions();
        options.skipDuplicates = true;
        options.allowPartialImport = true;
        return options;
    }

    public static ImportOptions of(String defaultCurrencyCode, String defaultDateFormat, Long targetAccountId,
            boolean createMissingCategories, boolean skipDuplicates, boolean allowPartialImport,
            String defaultType) {
        ImportOptions options = new ImportOptions();
        options.defaultCurrencyCode = blankToNull(defaultCurrencyCode);
        options.defaultDateFormat = blankToNull(defaultDateFormat);
        options.targetAccountId = targetAccountId;
        options.createMissingCategories = createMissingCategories;
        options.skipDuplicates = skipDuplicates;
        options.allowPartialImport = allowPartialImport;
        options.defaultType = blankToNull(defaultType);
        return options;
    }

    public String defaultCurrencyCode() {
        return defaultCurrencyCode;
    }

    public String defaultDateFormat() {
        return defaultDateFormat;
    }

    public Long targetAccountId() {
        return targetAccountId;
    }

    public boolean createMissingCategories() {
        return createMissingCategories;
    }

    public boolean skipDuplicates() {
        return skipDuplicates;
    }

    public boolean allowPartialImport() {
        return allowPartialImport;
    }

    public String defaultType() {
        return defaultType;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}