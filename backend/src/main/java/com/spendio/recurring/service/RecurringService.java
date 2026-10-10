package com.spendio.recurring.service;

import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.categories.domain.Category;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.recurring.dto.request.CreateRecurringRequest;
import com.spendio.recurring.dto.request.UpdateRecurringRequest;
import com.spendio.recurring.dto.response.CreateRecurringResponse;
import com.spendio.recurring.dto.response.OccurrencePreviewResponse;
import com.spendio.recurring.dto.response.RecurringTransactionResponse;
import com.spendio.recurring.mapper.RecurringMapper;
import com.spendio.recurring.repository.RecurringTransactionRepository;
import com.spendio.sharedkernel.domain.PeriodCalculator;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.RecurringRunStatus;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.error.exception.RecurringNotDueException;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.recurring.dto.response.RecurringRunResponse;
import com.spendio.transactions.service.TransactionService;
import com.spendio.transactions.support.LedgerFx;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recurring rule lifecycle (ARCHITECTURE.md D.9): create with the initial occurrence and
 * a five-date preview, partial edits that re-validate the merged schedule and reset the
 * retry budget, soft delete that keeps ledger history, and the pause/resume/cancel
 * transitions. Resume hands the bounded catch-up off to {@link RecurringExecutionService}
 * because it must span several transactions; everything else is single-tx.
 */
@Service
public class RecurringService {

    private final RecurringTransactionRepository rules;
    private final AccountQueryService accountQuery;
    private final CategoryQueryService categoryQuery;
    private final BaseCurrencyPort baseCurrency;
    private final ObjectProvider<FxConversionPort> fxPort;
    private final EntityManager entityManager;
    private final RecurrenceScheduleCalculator calculator;
    private final RecurringMapper mapper;
    private final RecurringQueryService query;
    private final RecurringExecutionService execution;
    private final TransactionService transactionService;

    public RecurringService(RecurringTransactionRepository rules, AccountQueryService accountQuery,
            CategoryQueryService categoryQuery, BaseCurrencyPort baseCurrency,
            ObjectProvider<FxConversionPort> fxPort, EntityManager entityManager,
            RecurrenceScheduleCalculator calculator, RecurringMapper mapper, RecurringQueryService query,
            RecurringExecutionService execution, TransactionService transactionService) {
        this.rules = rules;
        this.accountQuery = accountQuery;
        this.categoryQuery = categoryQuery;
        this.baseCurrency = baseCurrency;
        this.fxPort = fxPort;
        this.entityManager = entityManager;
        this.calculator = calculator;
        this.mapper = mapper;
        this.query = query;
        this.execution = execution;
        this.transactionService = transactionService;
    }

    @Transactional
    public CreateRecurringResponse create(Long userId, CreateRecurringRequest request) {
        LocalDate today = LocalDate.now();
        AccountResponse account = accountQuery.detail(userId, request.accountId());
        requireActive(account, "accountId");
        String currency = normalize(request.currencyCode());
        if (!currency.equals(account.currencyCode())) {
            throw validation("currencyCode", "must match the account currency " + account.currencyCode());
        }

        TransactionType type = request.type();
        AccountResponse toAccount = null;
        CategoryResponse category = null;
        if (type == TransactionType.TRANSFER) {
            if (request.categoryId() != null) {
                throw validation("categoryId", "must be empty for transfer rules");
            }
            if (request.toAccountId() == null) {
                throw validation("toAccountId", "is required for transfer rules");
            }
            if (request.toAccountId().equals(request.accountId())) {
                throw validation("toAccountId", "must differ from accountId");
            }
            toAccount = accountQuery.detail(userId, request.toAccountId());
            requireActive(toAccount, "toAccountId");
        } else if (type == TransactionType.INCOME || type == TransactionType.EXPENSE) {
            if (request.toAccountId() != null) {
                throw validation("toAccountId", "only applies to transfer rules");
            }
            if (request.categoryId() == null) {
                throw validation("categoryId", "is required for income and expense rules");
            }
            category = categoryQuery.detail(userId, request.categoryId());
            requireCategoryMatches(type, category);
        } else {
            throw validation("type", "must be INCOME, EXPENSE or TRANSFER");
        }

        validateSchedule(request.frequency(), request.dayOfMonth(), request.dayOfWeek(),
                request.targetOccurrenceDate(), request.startDate(), request.endDate());

        LocalDate anchor = request.startDate().isAfter(today) ? request.startDate() : today;
        LedgerFx.Freeze freeze = LedgerFx.freeze(baseCurrency.baseCurrencyCode(userId), fxPort.getIfAvailable(),
                request.amountMinor(), currency, anchor);

        RecurringTransaction rule = RecurringTransaction.create(
                userId,
                entityManager.getReference(com.spendio.accounts.domain.Account.class, request.accountId()),
                toAccount == null ? null
                        : entityManager.getReference(com.spendio.accounts.domain.Account.class, toAccount.id()),
                category == null ? null
                        : entityManager.getReference(Category.class, category.id()),
                type,
                type == TransactionType.INCOME ? TransactionDirection.INFLOW : TransactionDirection.OUTFLOW,
                request.amountMinor(),
                currency,
                freeze.exchangeRate(),
                freeze.baseAmountMinor(),
                trimToNull(request.description()),
                trimToNull(request.notes()),
                request.frequency(),
                request.intervalCount().shortValue(),
                request.dayOfMonth(),
                request.dayOfWeek(),
                Boolean.TRUE.equals(request.skipWeekends()),
                request.startDate(),
                request.endDate(),
                anchor,
                !Boolean.FALSE.equals(request.autoPost()),
                request.maxRetries() == null ? 3 : request.maxRetries().shortValue(),
                request.targetOccurrenceDate());

        LocalDate next = calculator.firstOnOrAfter(rule, anchor);
        if (next == null) {
            throw new BusinessException(ErrorCode.RECURRING_INVALID_SCHEDULE,
                    "Schedule produces no occurrence on or after " + anchor)
                    .withProperty("startDate", request.startDate())
                    .withProperty("endDate", request.endDate());
        }
        rule.setNextRunDate(next);
        rules.save(rule);

        List<OccurrencePreviewResponse.Occurrence> occurrences = calculator.preview(rule, anchor, 5).stream()
                .map(date -> mapper.toOccurrence(date, rule.getBaseAmountMinor(), true))
                .toList();
        return new CreateRecurringResponse(mapper.toResponse(rule), occurrences);
    }

    @Transactional
    public RecurringTransactionResponse update(Long userId, Long id, UpdateRecurringRequest request) {
        RecurringTransaction rule = rules.findOwnedForUpdate(userId, id).orElseThrow(() -> notFound(id));
        if (request.version() != null && !request.version().equals(rule.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "The rule was modified by another request")
                    .withProperty("expectedVersion", request.version())
                    .withProperty("currentVersion", rule.getVersion());
        }

        boolean typeChanged = request.type() != null && request.type() != rule.getType();
        boolean frequencyChanged = request.frequency() != null && request.frequency() != rule.getFrequency();
        if (typeChanged) {
            // Refs the new type cannot carry are cleared before the request's own refs
            // are applied, so a PATCH can both change type and supply the new refs.
            if (request.type() == TransactionType.TRANSFER) {
                rule.setCategory(null);
            } else {
                rule.setToAccount(null);
            }
        }
        if (frequencyChanged) {
            rule.setDayOfMonth(null);
            rule.setDayOfWeek(null);
        }

        if (request.accountId() != null) {
            AccountResponse account = accountQuery.detail(userId, request.accountId());
            requireActive(account, "accountId");
            rule.setAccount(entityManager.getReference(com.spendio.accounts.domain.Account.class, account.id()));
        }
        if (request.toAccountId() != null) {
            if (request.toAccountId().equals(rule.getAccount().getId())) {
                throw validation("toAccountId", "must differ from accountId");
            }
            AccountResponse toAccount = accountQuery.detail(userId, request.toAccountId());
            requireActive(toAccount, "toAccountId");
            rule.setToAccount(entityManager.getReference(com.spendio.accounts.domain.Account.class, toAccount.id()));
        }
        if (request.categoryId() != null) {
            CategoryResponse category = categoryQuery.detail(userId, request.categoryId());
            rule.setCategory(entityManager.getReference(Category.class, category.id()));
        }
        if (request.type() != null) {
            rule.setType(request.type());
        }
        if (request.frequency() != null) {
            rule.setFrequency(request.frequency());
        }
        if (request.amountMinor() != null) {
            rule.setAmountMinor(request.amountMinor());
        }
        if (request.currencyCode() != null) {
            rule.setCurrencyCode(normalize(request.currencyCode()));
        }
        if (request.description() != null) {
            rule.setDescription(trimToNull(request.description()));
        }
        if (request.notes() != null) {
            rule.setNotes(trimToNull(request.notes()));
        }
        if (request.intervalCount() != null) {
            rule.setIntervalCount(request.intervalCount().shortValue());
        }
        if (request.dayOfMonth() != null) {
            rule.setDayOfMonth(request.dayOfMonth());
        }
        if (request.dayOfWeek() != null) {
            rule.setDayOfWeek(request.dayOfWeek());
        }
        if (request.skipWeekends() != null) {
            rule.setSkipWeekends(request.skipWeekends());
        }
        if (request.startDate() != null) {
            rule.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            rule.setEndDate(request.endDate());
        }
        if (request.autoPost() != null) {
            rule.setAutoPost(request.autoPost());
        }
        if (request.maxRetries() != null) {
            rule.setMaxRetries(request.maxRetries().shortValue());
        }
        if (request.targetOccurrenceDate() != null) {
            rule.setTargetOccurrenceDate(request.targetOccurrenceDate());
        }

        validateMergedRule(userId, rule);
        rule.setDirection(rule.getType() == TransactionType.INCOME
                ? TransactionDirection.INFLOW
                : TransactionDirection.OUTFLOW);

        LocalDate today = LocalDate.now();
        LocalDate anchor = rule.getStartDate().isAfter(today) ? rule.getStartDate() : today;
        LedgerFx.Freeze freeze = LedgerFx.freeze(baseCurrency.baseCurrencyCode(userId), fxPort.getIfAvailable(),
                rule.getAmountMinor(), rule.getCurrencyCode(), anchor);
        rule.applyFrozenAmount(rule.getAmountMinor(), freeze.exchangeRate(), freeze.baseAmountMinor());

        rule.clearFailure();
        LocalDate next = calculator.firstOnOrAfter(rule, anchor);
        if (next == null) {
            if (rule.getStatus() != RecurringStatus.CANCELLED) {
                rule.markCompleted();
            }
        } else {
            rule.setNextRunDate(next);
            if (rule.getStatus() == RecurringStatus.COMPLETED) {
                rule.resume();
            }
        }
        return mapper.toResponse(rule);
    }

    @Transactional
    public RecurringTransactionResponse pause(Long userId, Long id) {
        RecurringTransaction rule = rules.findOwnedForUpdate(userId, id).orElseThrow(() -> notFound(id));
        if (rule.getStatus() == RecurringStatus.PAUSED) {
            throw new BusinessException(ErrorCode.RECURRING_ALREADY_PAUSED, "Recurring transaction is already paused");
        }
        requireTransitionable(rule, "pause");
        rule.pause();
        return mapper.toResponse(rule);
    }

    /**
     * Resume (ARCHITECTURE.md J.8): the state flip and the schedule jump commit first,
     * then the bounded catch-up generates at most {@code catch-up-limit} missed
     * occurrences, each in its own transaction.
     */
    public RecurringTransactionResponse resume(Long userId, Long id) {
        RecurringTransaction rule = requireOwned(userId, id);
        if (rule.getStatus() != RecurringStatus.PAUSED && rule.getStatus() != RecurringStatus.FAILED) {
            throw validation("status", "only paused or failed rules can be resumed");
        }
        execution.resumeCatchUp(rule.getId());
        return query.detail(userId, id);
    }

    @Transactional
    public RecurringTransactionResponse cancel(Long userId, Long id) {
        RecurringTransaction rule = rules.findOwnedForUpdate(userId, id).orElseThrow(() -> notFound(id));
        requireTransitionable(rule, "cancel");
        rule.cancel();
        return mapper.toResponse(rule);
    }

    /** Soft delete: ledger rows and the run log stay; only the rule pointer is dropped. */
    @Transactional
    public void delete(Long userId, Long id) {
        RecurringTransaction rule = rules.findOwnedForUpdate(userId, id).orElseThrow(() -> notFound(id));
        rule.softDelete();
        // unlinkRecurring clears the persistence context, so the delete must land first
        rules.flush();
        transactionService.unlinkRecurring(userId, id);
    }

    /**
     * Run-now (ARCHITECTURE.md J.8): executes the pending occurrence at its
     * {@code next_run_date} regardless of how far away that date is, advancing the
     * schedule on success. {@code RECURRING_NOT_DUE} is reserved for rules whose
     * start date has not arrived yet.
     */
    public RecurringRunResponse runNow(Long userId, Long id, String idempotencyKey) {
        RecurringTransaction rule = requireOwned(userId, id);
        if (rule.getStatus() != RecurringStatus.ACTIVE) {
            throw validation("status", "only active rules can run now");
        }
        if (rule.getStartDate().isAfter(LocalDate.now())) {
            throw new RecurringNotDueException(
                    "Rule starts on " + rule.getStartDate() + " and has no occurrence yet");
        }
        return execution.runNow(userId, rule.getId(), idempotencyKey);
    }

    /** Merged-state validation for PATCH: the whole rule must be coherent after the edit. */
    private void validateMergedRule(Long userId, RecurringTransaction rule) {
        AccountResponse account = accountQuery.detail(userId, rule.getAccount().getId());
        requireActive(account, "accountId");
        String currency = rule.getCurrencyCode();
        if (!currency.equals(account.currencyCode())) {
            throw validation("currencyCode", "must match the account currency " + account.currencyCode());
        }
        if (rule.getType() == TransactionType.TRANSFER) {
            if (rule.getCategory() != null) {
                throw validation("categoryId", "must be empty for transfer rules");
            }
            if (rule.getToAccount() == null) {
                throw validation("toAccountId", "is required for transfer rules");
            }
            if (rule.getToAccount().getId().equals(rule.getAccount().getId())) {
                throw validation("toAccountId", "must differ from accountId");
            }
            requireActive(accountQuery.detail(userId, rule.getToAccount().getId()), "toAccountId");
        } else if (rule.getType() == TransactionType.INCOME || rule.getType() == TransactionType.EXPENSE) {
            if (rule.getToAccount() != null) {
                throw validation("toAccountId", "only applies to transfer rules");
            }
            if (rule.getCategory() == null) {
                throw validation("categoryId", "is required for income and expense rules");
            }
            CategoryResponse category = categoryQuery.detail(userId, rule.getCategory().getId());
            requireCategoryMatches(rule.getType(), category);
        } else {
            throw validation("type", "must be INCOME, EXPENSE or TRANSFER");
        }
        validateSchedule(rule.getFrequency(), rule.getDayOfMonth(), rule.getDayOfWeek(),
                rule.getTargetOccurrenceDate(), rule.getStartDate(), rule.getEndDate());
    }

    /**
     * Frequency/field matrix (ARCHITECTURE.md D.9): MONTHLY ignores dayOfWeek, WEEKLY
     * ignores dayOfMonth, DAILY/YEARLY take neither, CUSTOM needs its anchor date and
     * no day fields; mismatches are {@code RECURRING_FREQUENCY_FIELD_MISMATCH}.
     */
    static void validateSchedule(RecurrenceFrequency frequency, Integer dayOfMonth, Integer dayOfWeek,
            LocalDate targetOccurrenceDate, LocalDate startDate, LocalDate endDate) {
        switch (frequency) {
            case MONTHLY -> {
                if (dayOfWeek != null) {
                    throw mismatch("dayOfWeek", "must be empty for monthly rules");
                }
            }
            case WEEKLY -> {
                if (dayOfMonth != null) {
                    throw mismatch("dayOfMonth", "must be empty for weekly rules");
                }
            }
            case DAILY, YEARLY -> {
                if (dayOfMonth != null) {
                    throw mismatch("dayOfMonth", "must be empty for daily and yearly rules");
                }
                if (dayOfWeek != null) {
                    throw mismatch("dayOfWeek", "must be empty for daily and yearly rules");
                }
            }
            case CUSTOM -> {
                if (targetOccurrenceDate == null) {
                    throw mismatch("targetOccurrenceDate", "is required for custom rules");
                }
                if (dayOfMonth != null) {
                    throw mismatch("dayOfMonth", "must be empty for custom rules");
                }
                if (dayOfWeek != null) {
                    throw mismatch("dayOfWeek", "must be empty for custom rules");
                }
            }
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.RECURRING_INVALID_SCHEDULE, "endDate must not be before startDate")
                    .withProperty("startDate", startDate)
                    .withProperty("endDate", endDate);
        }
    }

    private void requireTransitionable(RecurringTransaction rule, String action) {
        if (rule.getStatus() == RecurringStatus.CANCELLED || rule.getStatus() == RecurringStatus.COMPLETED) {
            throw validation("status", "a " + rule.getStatus().name().toLowerCase()
                    + " rule cannot be " + (action.equals("cancel") ? "cancelled" : "paused"));
        }
    }

    private void requireActive(AccountResponse account, String field) {
        if (account.status() == AccountStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.ACCOUNT_ARCHIVED, "Account is archived")
                    .withProperty(field, account.id());
        }
    }

    private static void requireCategoryMatches(TransactionType type, CategoryResponse category) {
        if ((type == TransactionType.INCOME) != (category.type() == Category.Type.INCOME)) {
            throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH,
                    "Category does not match the transaction type")
                    .withProperty("categoryId", category.id())
                    .withProperty("type", type.name());
        }
    }

    private RecurringTransaction requireOwned(Long userId, Long id) {
        return rules.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound(id));
    }

    private static BusinessException notFound(Long id) {
        return new BusinessException(ErrorCode.RECURRING_NOT_FOUND, "Recurring transaction not found")
                .withProperty("id", id);
    }

    private static BusinessException validation(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                .withFieldError(field, message);
    }

    private static BusinessException mismatch(String field, String message) {
        return new BusinessException(ErrorCode.RECURRING_FREQUENCY_FIELD_MISMATCH,
                "Schedule fields do not match the frequency")
                .withFieldError(field, message);
    }

    private static String normalize(String currency) {
        return currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
