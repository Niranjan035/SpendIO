package com.spendio.transactions.service;

import com.spendio.accounts.service.AccountQueryService;
import com.spendio.accounts.spec.AccountFilter;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.dto.response.CategoryTreeResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.request.CheckDuplicateRequest;
import com.spendio.transactions.dto.response.DuplicateCheckResponse;
import com.spendio.transactions.dto.response.DuplicateCheckResponse.Confidence;
import com.spendio.transactions.dto.response.TransactionFilterOptionsResponse;
import com.spendio.transactions.dto.response.TransactionFilterOptionsResponse.AccountOption;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.dto.response.TransactionSummaryResponse;
import com.spendio.transactions.mapper.TransactionMapper;
import com.spendio.transactions.repository.TransactionRepository;
import com.spendio.transactions.repository.TransactionSummaryRepository;
import com.spendio.transactions.spec.TransactionFilter;
import com.spendio.transactions.spec.TransactionSpecifications;
import com.spendio.transactions.support.DedupeKeyFactory;
import com.spendio.transactions.support.TransactionCsvWriter;
import com.spendio.transactions.support.TransactionSortSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger reads (ARCHITECTURE.md E.6): filtered search with whitelist sort, detail,
 * summary rollup, filter sidebar options, CSV export and the duplicate probe.
 */
@Service
public class TransactionQueryService {

    private static final int EXPORT_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final int OPTIONS_ACCOUNT_PAGE_SIZE = 100;

    private final TransactionRepository transactions;
    private final TransactionSummaryRepository summaries;
    private final TransactionMapper mapper;
    private final AccountQueryService accountQuery;
    private final CategoryQueryService categoryQuery;
    private final BaseCurrencyPort baseCurrency;

    public TransactionQueryService(TransactionRepository transactions, TransactionSummaryRepository summaries,
            TransactionMapper mapper, AccountQueryService accountQuery, CategoryQueryService categoryQuery,
            BaseCurrencyPort baseCurrency) {
        this.transactions = transactions;
        this.summaries = summaries;
        this.mapper = mapper;
        this.accountQuery = accountQuery;
        this.categoryQuery = categoryQuery;
        this.baseCurrency = baseCurrency;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Long userId, Integer page, Integer size, String sort,
            TransactionFilter filter) {
        String base = baseCurrency.baseCurrencyCode(userId);
        Page<Transaction> result = transactions.findAll(fullSpec(userId, filter, base),
                TransactionSortSupport.pageable(page, size, sort));
        return PageResponse.from(result.map(tx -> mapper.toResponse(tx, base)));
    }

    @Transactional(readOnly = true)
    public TransactionResponse detail(Long userId, Long id) {
        return mapper.toResponse(requireOwned(userId, id), baseCurrency.baseCurrencyCode(userId));
    }

    @Transactional(readOnly = true)
    public TransactionSummaryResponse summary(Long userId, TransactionFilter filter) {
        String base = baseCurrency.baseCurrencyCode(userId);
        TransactionSummaryRepository.Summary totals =
                summaries.summarize(TransactionSpecifications.forFilter(userId, filter, base));
        return new TransactionSummaryResponse(
                totals.count(),
                totals.inflowBaseMinor(),
                totals.outflowBaseMinor(),
                totals.inflowBaseMinor() - totals.outflowBaseMinor(),
                base);
    }

    @Transactional(readOnly = true)
    public TransactionFilterOptionsResponse options(Long userId) {
        List<AccountOption> accounts = accountQuery
                .list(userId, 0, OPTIONS_ACCOUNT_PAGE_SIZE, "name,asc", new AccountFilter(null, null, null, null, true, null))
                .content().stream()
                .map(account -> new AccountOption(account.id(), account.name(), account.currencyCode(), account.status()))
                .toList();
        List<CategoryResponse> categories = new ArrayList<>();
        flatten(categoryQuery.tree(userId, null), categories);
        TransactionSummaryRepository.FilterFacets facets = summaries.facets(userId);
        return new TransactionFilterOptionsResponse(accounts, categories, facets.currencies(),
                facets.minDate(), facets.maxDate(), facets.typeCounts());
    }

    @Transactional(readOnly = true)
    public String export(Long userId, TransactionFilter filter, String sort) {
        String base = baseCurrency.baseCurrencyCode(userId);
        Specification<Transaction> spec = fullSpec(userId, filter, base);
        List<TransactionResponse> rows = new ArrayList<>();
        Pageable pageable = TransactionSortSupport.pageable(0, EXPORT_PAGE_SIZE, sort);
        Page<Transaction> page;
        do {
            page = transactions.findAll(spec, pageable);
            page.forEach(tx -> rows.add(mapper.toResponse(tx, base)));
            pageable = page.nextPageable();
        } while (page.hasNext() && rows.size() < MAX_EXPORT_ROWS);
        return TransactionCsvWriter.write(rows);
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResponse checkDuplicate(Long userId, CheckDuplicateRequest request) {
        String currency = request.currencyCode().trim().toUpperCase();
        List<Transaction> probes = transactions.findByUserIdAndOccurredOnAndAmountMinorAndCurrencyCodeAndDeletedAtIsNull(
                userId, request.occurredOn(), request.amountMinor(), currency);
        String wanted = DedupeKeyFactory.normaliseDescription(request.description());

        Transaction exactSameAccount = newestMatching(probes, wanted, request.accountId());
        Transaction exact = exactSameAccount != null ? exactSameAccount : newestMatching(probes, wanted, null);
        if (exact != null) {
            return new DuplicateCheckResponse(true, exact.getId(), Confidence.HIGH,
                    "An identical transaction already exists");
        }
        if (probes.isEmpty()) {
            return new DuplicateCheckResponse(false, null, Confidence.NONE, "No similar transaction found");
        }
        return new DuplicateCheckResponse(false, null, Confidence.LOW,
                "Found " + probes.size() + " transaction(s) with the same date, amount and currency");
    }

    private Transaction newestMatching(List<Transaction> probes, String normalisedDescription, Long accountId) {
        Comparator<Transaction> newestFirst = Comparator.comparing(Transaction::getId);
        return probes.stream()
                .filter(tx -> Objects.equals(DedupeKeyFactory.normaliseDescription(tx.getDescription()),
                        normalisedDescription))
                .filter(tx -> accountId == null || Objects.equals(tx.getAccount().getId(), accountId))
                .max(newestFirst)
                .orElse(null);
    }

    private Specification<Transaction> fullSpec(Long userId, TransactionFilter filter, String base) {
        return TransactionSpecifications.withReferences()
                .and(TransactionSpecifications.forFilter(userId, filter, base));
    }

    private Transaction requireOwned(Long userId, Long id) {
        return transactions.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found")
                        .withProperty("id", id));
    }

    private static void flatten(CategoryTreeResponse node, List<CategoryResponse> out) {
        if (node == null) {
            return;
        }
        if (node.category() != null) {
            out.add(node.category());
        }
        if (node.children() != null) {
            node.children().forEach(child -> flatten(child, out));
        }
    }
}
