package com.spendio.recurring.service;

import com.spendio.recurring.domain.RecurringRun;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.recurring.dto.response.OccurrencePreviewResponse;
import com.spendio.recurring.dto.response.RecurringRunResponse;
import com.spendio.recurring.dto.response.RecurringTransactionResponse;
import com.spendio.recurring.mapper.RecurringMapper;
import com.spendio.recurring.repository.RecurringRunRepository;
import com.spendio.recurring.repository.RecurringTransactionRepository;
import com.spendio.recurring.spec.RecurringFilter;
import com.spendio.recurring.spec.RecurringSpecifications;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.web.PageRequestFactory;
import com.spendio.sharedkernel.web.PageResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recurring reads (ARCHITECTURE.md D.9): filtered list, detail, execution history and
 * the occurrence preview behind {@code /preview} and the create response.
 */
@Service
public class RecurringQueryService {

    private final RecurringTransactionRepository rules;
    private final RecurringRunRepository runs;
    private final RecurringMapper mapper;
    private final RecurrenceScheduleCalculator calculator;

    public RecurringQueryService(RecurringTransactionRepository rules, RecurringRunRepository runs,
            RecurringMapper mapper, RecurrenceScheduleCalculator calculator) {
        this.rules = rules;
        this.runs = runs;
        this.mapper = mapper;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public PageResponse<RecurringTransactionResponse> list(Long userId, RecurringFilter filter, Integer page,
            Integer size) {
        LocalDate today = LocalDate.now();
        Pageable pageable = PageRequestFactory.of(page, size,
                Sort.by(Sort.Direction.ASC, "nextRunDate").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<RecurringTransaction> result = rules.findAll(
                RecurringSpecifications.forFilter(userId, filter, today)
                        .and(RecurringSpecifications.withReferences()),
                pageable);
        return PageResponse.from(result.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RecurringTransactionResponse detail(Long userId, Long id) {
        return mapper.toResponse(requireOwned(userId, id));
    }

    /** Everything due within the next {@code days} days, for the dashboard card. */
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> upcoming(Long userId, Integer days) {
        int window = days == null ? 7 : Math.max(1, Math.min(days, 90));
        return rules.findAll(RecurringSpecifications.upcoming(userId, LocalDate.now(), window),
                        PageRequestFactory.of(0, PageRequestFactory.MAX_SIZE,
                                Sort.by(Sort.Direction.ASC, "nextRunDate").and(Sort.by(Sort.Direction.ASC, "id"))))
                .map(mapper::toResponse)
                .getContent();
    }

    @Transactional(readOnly = true)
    public PageResponse<RecurringRunResponse> runs(Long userId, Long id, Integer page, Integer size) {
        requireOwned(userId, id);
        Pageable pageable = PageRequestFactory.of(page, size,
                Sort.by(Sort.Direction.DESC, "scheduledFor").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<RecurringRun> result = runs.findByRecurringTransactionId(id, pageable);
        return PageResponse.from(result.map(mapper::toRunResponse));
    }

    /** Next {@code count} occurrences (default 5, clamp 1..31) from max(today, startDate). */
    @Transactional(readOnly = true)
    public OccurrencePreviewResponse preview(Long userId, Long id, Integer count) {
        RecurringTransaction rule = requireOwned(userId, id);
        int wanted = count == null ? 5 : Math.max(1, Math.min(count, 31));
        LocalDate anchor = rule.getStartDate().isAfter(LocalDate.now()) ? rule.getStartDate() : LocalDate.now();
        boolean willRun = rule.getStatus() == RecurringStatus.ACTIVE;
        List<OccurrencePreviewResponse.Occurrence> occurrences = calculator.preview(rule, anchor, wanted).stream()
                .map(date -> mapper.toOccurrence(date, rule.getBaseAmountMinor(), willRun))
                .toList();
        return new OccurrencePreviewResponse(occurrences);
    }

    private RecurringTransaction requireOwned(Long userId, Long id) {
        return rules.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECURRING_NOT_FOUND, "Recurring transaction not found")
                        .withProperty("id", id));
    }
}
