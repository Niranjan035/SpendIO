package com.spendio.accounts.service;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.mapper.AccountMapper;
import com.spendio.accounts.repository.AccountBalanceRepository;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.accounts.spec.AccountFilter;
import com.spendio.accounts.spec.AccountSpecifications;
import com.spendio.accounts.support.AccountOwnershipValidator;
import com.spendio.accounts.support.BalanceLine;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.sharedkernel.web.SortRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account reads (ARCHITECTURE.md D.6): paginated list with optional native filters,
 * and the balance-carrying detail read. Sorting runs in memory because two of the four
 * sortable fields - balance and hasTransactions - cannot be pushed into JPA, and an
 * account list is small by construction.
 */
@Service
public class AccountQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AccountRepository accounts;
    private final AccountBalanceRepository ledger;
    private final AccountOwnershipValidator ownership;
    private final AccountBalanceService balanceService;

    public AccountQueryService(AccountRepository accounts, AccountBalanceRepository ledger,
            AccountOwnershipValidator ownership, AccountBalanceService balanceService) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.ownership = ownership;
        this.balanceService = balanceService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> list(Long userId, Integer page, Integer size, String sort,
            AccountFilter filter) {
        SortRequest sortRequest = SortRequest.parse(sort, sortableFields());
        List<Account> entities = new ArrayList<>(loadAll(userId, filter));
        Map<Long, Long> balances = balanceOf(entities);
        entities.sort(comparator(sortRequest.property(), balances));
        if (sortRequest.direction() == Sort.Direction.DESC) {
            java.util.Collections.reverse(entities);
        }

        int pageIndex = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int from = Math.min(pageIndex * pageSize, entities.size());
        int to = Math.min(from + pageSize, entities.size());

        Map<Long, Long> counts = ledger.transactionCounts(entities.isEmpty() ? userId : entities.get(0).getUserId());
        List<AccountResponse> content = entities.subList(from, to).stream()
                .map(account -> toResponse(userId, account, balances, counts))
                .toList();
        return PageResponse.of(content, pageIndex, pageSize, entities.size());
    }

    @Transactional(readOnly = true)
    public AccountResponse detail(Long userId, Long accountId) {
        Account account = ownership.requireOwned(userId, accountId);
        Map<Long, Long> balances = ledger.balanceLines(userId).stream()
                .collect(Collectors.toMap(BalanceLine::accountId, BalanceLine::balance));
        return toResponse(userId, account, balances, Map.of(accountId, ledger.countTransactions(userId, accountId)));
    }

    /**
     * The user's primary account (ARCHITECTURE.md D.11): the implicit source leg for
     * transfer-linked goal contributions, which move money from the primary account
     * into the goal's linked account.
     */
    @Transactional(readOnly = true)
    public AccountResponse primaryAccount(Long userId) {
        Long accountId = accounts.findFirstByUserIdAndPrimaryAccountTrueAndDeletedAtIsNull(userId)
                .map(Account::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Account not found")
                        .withProperty("primary", true)
                        .withProperty("userId", userId));
        return detail(userId, accountId);
    }

    private List<Account> loadAll(Long userId, AccountFilter filter) {
        if (filter != null && filter.hasTransactions() != null) {
            return accounts.findAllById(ledger.matchingIds(userId, filter));
        }
        return accounts.findAll(AccountSpecifications.forFilter(userId, filter));
    }

    private Map<Long, Long> balanceOf(List<Account> entities) {
        if (entities.isEmpty()) {
            return Map.of();
        }
        return ledger.balanceLines(entities.get(0).getUserId()).stream()
                .collect(Collectors.toMap(BalanceLine::accountId, BalanceLine::balance));
    }

    private AccountResponse toResponse(Long userId, Account account, Map<Long, Long> balances,
            Map<Long, Long> counts) {
        long balance = balances.getOrDefault(account.getId(), account.getOpeningBalanceMinor());
        String base = balanceService.baseCurrencyCode(userId);
        long balanceBase = balanceService.toBase(userId, balance, account.getCurrencyCode());
        Long availableCredit = account.getType() == AccountType.CREDIT_CARD && account.getCreditLimitMinor() != null
                ? account.getCreditLimitMinor() - Math.max(0, -balance)
                : null;
        return AccountMapper.toResponse(account, balance, balanceBase, base, availableCredit,
                counts.getOrDefault(account.getId(), 0L));
    }

    private static Comparator<Account> comparator(String property, Map<Long, Long> balances) {
        if (property == null) {
            return byName();
        }
        return switch (property) {
            case "balance" -> Comparator.comparingLong((Account a) -> balances.getOrDefault(a.getId(), 0L));
            case "openingDate" -> Comparator.comparing(Account::getOpeningBalanceDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt" -> Comparator.comparing(Account::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> byName();
        };
    }

    private static Comparator<Account> byName() {
        return Comparator.comparing(Account::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private static List<String> sortableFields() {
        return List.of("name", "balance", "openingDate", "createdAt");
    }
}