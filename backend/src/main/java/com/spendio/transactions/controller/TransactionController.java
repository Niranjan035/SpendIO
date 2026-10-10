package com.spendio.transactions.controller;

import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.transactions.dto.request.BulkCategoryRequest;
import com.spendio.transactions.dto.request.BulkDeleteRequest;
import com.spendio.transactions.dto.request.CheckDuplicateRequest;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.dto.request.ReverseTransactionRequest;
import com.spendio.transactions.dto.request.UpdateTransactionRequest;
import com.spendio.transactions.dto.response.BulkUpdateResponse;
import com.spendio.transactions.dto.response.DuplicateCheckResponse;
import com.spendio.transactions.dto.response.TransactionFilterOptionsResponse;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.dto.response.TransactionSummaryResponse;
import com.spendio.transactions.service.TransactionQueryService;
import com.spendio.transactions.service.TransactionReversalService;
import com.spendio.transactions.service.TransactionService;
import com.spendio.transactions.spec.TransactionFilter;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ledger endpoints (ARCHITECTURE.md D.8, E.6/E.7): search, create (with optional
 * {@code Idempotency-Key}), summary, filter options, detail, patch, delete, bulk
 * actions, CSV export, the duplicate probe, and single-row reversal.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;
    private final TransactionQueryService query;
    private final TransactionReversalService reversal;

    public TransactionController(TransactionService service, TransactionQueryService query,
            TransactionReversalService reversal) {
        this.service = service;
        this.query = query;
        this.reversal = reversal;
    }

    @GetMapping
    public PageResponse<TransactionResponse> search(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> accountId,
            @RequestParam(required = false) List<Long> categoryId,
            @RequestParam(required = false) Category.Type categoryType,
            @RequestParam(required = false) List<TransactionType> type,
            @RequestParam(required = false) List<TransactionDirection> direction,
            @RequestParam(required = false) List<TransactionStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long minAmountMinor,
            @RequestParam(required = false) Long maxAmountMinor,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Boolean baseCurrencyOnly,
            @RequestParam(required = false) Boolean recurringOnly,
            @RequestParam(required = false) Boolean importedOnly,
            @RequestParam(required = false) Boolean hasNotes,
            @RequestParam(required = false) Boolean hasReversal,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return query.search(userId, page, size, sort,
                filterOf(q, accountId, categoryId, categoryType, type, direction, status, from, to,
                        minAmountMinor, maxAmountMinor, currencyCode, baseCurrencyOnly, recurringOnly,
                        importedOnly, hasNotes, hasReversal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return service.create(userId, request, idempotencyKey);
    }

    @GetMapping("/summary")
    public TransactionSummaryResponse summary(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> accountId,
            @RequestParam(required = false) List<Long> categoryId,
            @RequestParam(required = false) Category.Type categoryType,
            @RequestParam(required = false) List<TransactionType> type,
            @RequestParam(required = false) List<TransactionDirection> direction,
            @RequestParam(required = false) List<TransactionStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long minAmountMinor,
            @RequestParam(required = false) Long maxAmountMinor,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Boolean baseCurrencyOnly,
            @RequestParam(required = false) Boolean recurringOnly,
            @RequestParam(required = false) Boolean importedOnly,
            @RequestParam(required = false) Boolean hasNotes,
            @RequestParam(required = false) Boolean hasReversal) {
        return query.summary(userId,
                filterOf(q, accountId, categoryId, categoryType, type, direction, status, from, to,
                        minAmountMinor, maxAmountMinor, currencyCode, baseCurrencyOnly, recurringOnly,
                        importedOnly, hasNotes, hasReversal));
    }

    @GetMapping("/filters/options")
    public TransactionFilterOptionsResponse options(@CurrentUserId Long userId) {
        return query.options(userId);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> accountId,
            @RequestParam(required = false) List<Long> categoryId,
            @RequestParam(required = false) Category.Type categoryType,
            @RequestParam(required = false) List<TransactionType> type,
            @RequestParam(required = false) List<TransactionDirection> direction,
            @RequestParam(required = false) List<TransactionStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long minAmountMinor,
            @RequestParam(required = false) Long maxAmountMinor,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) Boolean baseCurrencyOnly,
            @RequestParam(required = false) Boolean recurringOnly,
            @RequestParam(required = false) Boolean importedOnly,
            @RequestParam(required = false) Boolean hasNotes,
            @RequestParam(required = false) Boolean hasReversal,
            @RequestParam(required = false) String sort) {
        String csv = query.export(userId,
                filterOf(q, accountId, categoryId, categoryType, type, direction, status, from, to,
                        minAmountMinor, maxAmountMinor, currencyCode, baseCurrencyOnly, recurringOnly,
                        importedOnly, hasNotes, hasReversal),
                sort);
        String filename = "spendio-transactions-" + LocalDate.now()
                .format(DateTimeFormatter.ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    @PostMapping("/check-duplicate")
    public DuplicateCheckResponse checkDuplicate(@CurrentUserId Long userId,
            @Valid @RequestBody CheckDuplicateRequest request) {
        return query.checkDuplicate(userId, request);
    }

    @PostMapping("/bulk")
    public BulkUpdateResponse bulkUpdate(@CurrentUserId Long userId, @Valid @RequestBody BulkCategoryRequest request) {
        return service.recategorise(userId, request);
    }

    @DeleteMapping("/bulk")
    public BulkUpdateResponse bulkDelete(@CurrentUserId Long userId, @Valid @RequestBody BulkDeleteRequest request) {
        return service.bulkDelete(userId, request);
    }

    @GetMapping("/{id}")
    public TransactionResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return query.detail(userId, id);
    }

    @PatchMapping("/{id}")
    public TransactionResponse update(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse reverse(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody ReverseTransactionRequest request) {
        return reversal.reverse(userId, id, request);
    }

    private static TransactionFilter filterOf(String q, List<Long> accountIds, List<Long> categoryIds,
            Category.Type categoryType, List<TransactionType> types, List<TransactionDirection> directions,
            List<TransactionStatus> statuses, LocalDate from, LocalDate to, Long minAmountMinor,
            Long maxAmountMinor, String currencyCode, Boolean baseCurrencyOnly, Boolean recurringOnly,
            Boolean importedOnly, Boolean hasNotes, Boolean hasReversal) {
        return new TransactionFilter(q, accountIds, categoryIds, categoryType, types, directions, statuses,
                from, to, minAmountMinor, maxAmountMinor, currencyCode, baseCurrencyOnly, recurringOnly,
                importedOnly, hasNotes, hasReversal);
    }
}
