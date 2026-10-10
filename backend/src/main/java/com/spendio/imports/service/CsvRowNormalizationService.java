package com.spendio.imports.service;

import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.categories.service.CategoryService;
import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.domain.ColumnMapping;
import com.spendio.imports.domain.ImportOptions;
import com.spendio.imports.matcher.AccountMatcher;
import com.spendio.imports.matcher.AmountParser;
import com.spendio.imports.matcher.CategoryMatcher;
import com.spendio.imports.matcher.DateParser;
import com.spendio.imports.matcher.DescriptionNormalizer;
import com.spendio.imports.repository.CsvImportRowRepository;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import com.spendio.sharedkernel.domain.enums.ImportStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Row normalisation (ARCHITECTURE.md D.12 step 4): applies the saved mapping to every
 * pending row, matching accounts/categories, and marks each row VALID or INVALID.
 */
@Service
public class CsvRowNormalizationService {

    private final CsvImportService importService;
    private final CsvImportRowRepository rows;
    private final AccountQueryService accounts;
    private final CategoryQueryService categories;
    private final CategoryService categoryService;

    public CsvRowNormalizationService(CsvImportService importService, CsvImportRowRepository rows,
            AccountQueryService accounts, CategoryQueryService categories, CategoryService categoryService) {
        this.importService = importService;
        this.rows = rows;
        this.accounts = accounts;
        this.categories = categories;
        this.categoryService = categoryService;
    }

    @Transactional
    public void normalize(Long userId, Long importId) {
        CsvImport job = importService.getOwned(userId, importId);
        if (job.getStatus() != ImportStatus.MAPPING_REQUIRED && job.getStatus() != ImportStatus.VALIDATING
                && job.getStatus() != ImportStatus.READY) {
            throw new BusinessException(ErrorCode.IMPORT_NOT_READY, "Import is not ready for this action")
                    .withProperty("importId", importId)
                    .withProperty("status", job.getStatus());
        }
        ColumnMapping mapping = job.getMapping();
        ImportOptions options = job.getOptions();
        List<CsvImportRow> pending = rows.findByImportIdAndStatus(importId, ImportRowStatus.PENDING);
        List<AccountResponse> accountList = accounts.list(userId, null, null, null,
                com.spendio.accounts.spec.AccountFilter.all()).content();
        List<AccountMatcher.Candidate> accountCandidates = accountList.stream()
                .map(a -> new AccountMatcher.Candidate(a.id(), a.name()))
                .toList();
        List<CategoryResponse> categoryList = categories.allActive(userId);
        List<CategoryMatcher.Candidate> categoryCandidates = categoryList.stream()
                .map(c -> new CategoryMatcher.Candidate(c.id(), c.name(), c.type()))
                .toList();
        int valid = 0;
        int invalid = 0;
        for (var row : pending) {
            Map<String, Object> errors = new HashMap<>();
            var parsed = normalizeRow(userId, row, mapping, options, accountCandidates, categoryCandidates, errors);
            if (errors.isEmpty()) {
                row.storeParsed(parsed, (LocalDate) parsed.get("occurredOn"), (Long) parsed.get("amountMinor"),
                        (String) parsed.get("currencyCode"), (String) parsed.get("description"),
                        (String) parsed.get("type"), null);
                row.setMatchedAccount((Long) parsed.get("matchedAccountId"), "NORMALIZED",
                        (BigDecimal) parsed.get("confidence"));
                row.setMatchedCategory((Long) parsed.get("matchedCategoryId"), "NORMALIZED",
                        (BigDecimal) parsed.get("confidence"));
                valid++;
            } else {
                row.storeParsed(parsed, (LocalDate) parsed.get("occurredOn"), (Long) parsed.get("amountMinor"),
                        (String) parsed.get("currencyCode"), (String) parsed.get("description"),
                        (String) parsed.get("type"), errors.values().stream().map(String::valueOf).toList());
                invalid++;
            }
            rows.save(row);
        }
        job.setTotals(valid, invalid, 0);
        job.markValidating();
    }

    private Map<String, Object> normalizeRow(Long userId, CsvImportRow row, ColumnMapping mapping,
            ImportOptions options, List<AccountMatcher.Candidate> accountCandidates,
            List<CategoryMatcher.Candidate> categoryCandidates, Map<String, Object> errors) {
        Map<String, Object> raw = row.getRawJson();
        Map<String, Object> parsed = new HashMap<>();
        String dateColumn = mapping.getDateColumn();
        String rawDate = dateColumn == null ? null : stringValue(raw, dateColumn);
        LocalDate date = null;
        if (rawDate == null || rawDate.isBlank()) {
            errors.put("date", "date column is required");
        } else {
            Optional<LocalDate> d = DateParser.tryParse(rawDate, mapping.getDateFormat());
            if (d.isPresent()) {
                date = d.get();
            } else {
                errors.put("date", "cannot parse date: " + rawDate);
            }
        }
        parsed.put("occurredOn", date);

        long amountMinor = 0L;
        String amountColumn = mapping.getAmountColumn();
        String rawAmount = amountColumn == null ? null : stringValue(raw, amountColumn);
        if (rawAmount == null || rawAmount.isBlank()) {
            errors.put("amount", "amount column is required");
        } else {
            OptionalLong a = AmountParser.parseMinor(rawAmount);
            if (a.isPresent()) {
                amountMinor = Math.abs(a.getAsLong());
            } else {
                errors.put("amount", "cannot parse amount: " + rawAmount);
            }
        }
        parsed.put("amountMinor", amountMinor);

        String currencyColumn = mapping.getCurrencyColumn();
        String currency = currencyColumn == null ? null : stringValue(raw, currencyColumn);
        if (currency == null || currency.isBlank()) {
            currency = options.defaultCurrencyCode();
        }
        if (currency == null || currency.isBlank()) {
            errors.put("currency", "currency column is required");
        } else {
            currency = currency.trim().toUpperCase();
        }
        parsed.put("currencyCode", currency);

        String descriptionColumn = mapping.getDescriptionColumn();
        String description = descriptionColumn == null ? null : stringValue(raw, descriptionColumn);
        parsed.put("description", description == null || description.isBlank() ? null : description.trim());

        String typeColumn = mapping.getTypeColumn();
        String rawType = typeColumn == null ? null : stringValue(raw, typeColumn);
        TransactionType type = null;
        if (rawType != null && !rawType.isBlank()) {
            try {
                type = TransactionType.valueOf(rawType.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.put("type", "unknown transaction type: " + rawType);
            }
        }
        if (type == null) {
            type = amountMinor >= 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
        }
        if (type == TransactionType.TRANSFER) {
            errors.put("type", "transfer type cannot be imported");
        }
        parsed.put("type", type.name());

        Long accountId = null;
        Long matchedAccountId = null;
        BigDecimal confidence = BigDecimal.ZERO;
        String accountColumn = mapping.getAccountColumn();
        String rawAccount = accountColumn == null ? null : stringValue(raw, accountColumn);
        if (rawAccount != null && !rawAccount.isBlank()) {
            Optional<AccountMatcher.Match> match = AccountMatcher.match(rawAccount, accountCandidates);
            if (match.isPresent()) {
                accountId = match.get().candidateId();
                matchedAccountId = accountId;
                confidence = match.get().confidence();
            } else {
                errors.put("account", "no account matched: " + rawAccount);
            }
        } else if (options.targetAccountId() != null) {
            accountId = options.targetAccountId();
            matchedAccountId = accountId;
            confidence = BigDecimal.ONE;
        } else {
            errors.put("account", "account column or target account is required");
        }
        parsed.put("accountId", accountId);
        parsed.put("matchedAccountId", matchedAccountId);

        Long categoryId = null;
        Long matchedCategoryId = null;
        String categoryColumn = mapping.getCategoryColumn();
        String rawCategory = categoryColumn == null ? null : stringValue(raw, categoryColumn);
        if (rawCategory != null && !rawCategory.isBlank()) {
            var categoryType = type == TransactionType.INCOME
                    ? com.spendio.categories.domain.Category.Type.INCOME
                    : com.spendio.categories.domain.Category.Type.EXPENSE;
            Optional<AccountMatcher.Match> match = CategoryMatcher.match(rawCategory, categoryType,
                    categoryCandidates);
            if (match.isPresent()) {
                categoryId = match.get().candidateId();
                matchedCategoryId = categoryId;
            } else if (options.createMissingCategories()) {
                try {
                    var created = categoryService.create(userId,
                            new CreateCategoryRequest(rawCategory.trim(), type.name(), null, null, null, null));
                    categoryId = created.getId();
                    matchedCategoryId = categoryId;
                } catch (RuntimeException createFailed) {
                    errors.put("category", "cannot create category: " + rawCategory);
                }
            } else {
                errors.put("category", "no category matched: " + rawCategory);
            }
        }
        parsed.put("categoryId", categoryId);
        parsed.put("matchedCategoryId", matchedCategoryId);
        parsed.put("confidence", confidence);
        parsed.put("type", type.name());
        return parsed;
    }

    private String stringValue(Map<String, Object> raw, String column) {
        Object v = raw == null ? null : raw.get(column);
        return v == null ? null : String.valueOf(v);
    }
}