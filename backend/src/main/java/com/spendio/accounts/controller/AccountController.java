package com.spendio.accounts.controller;

import com.spendio.accounts.dto.request.AdjustBalanceRequest;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.dto.request.UpdateAccountRequest;
import com.spendio.accounts.dto.response.AccountBalanceResponse;
import com.spendio.accounts.dto.response.AccountResponse;
import com.spendio.accounts.dto.response.AccountTotalsResponse;
import com.spendio.accounts.dto.response.DailyBalancePointResponse;
import com.spendio.accounts.spec.AccountFilter;
import com.spendio.accounts.service.AccountArchiveService;
import com.spendio.accounts.service.AccountBalanceService;
import com.spendio.accounts.service.AccountQueryService;
import com.spendio.accounts.service.AccountService;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account endpoints (ARCHITECTURE.md E.5, E.6): CRUD, archive/restore, primary flag,
 * balances and the ADJUSTMENT write.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final AccountQueryService queryService;
    private final AccountArchiveService archiveService;
    private final AccountBalanceService balanceService;

    public AccountController(AccountService accountService, AccountQueryService queryService,
            AccountArchiveService archiveService, AccountBalanceService balanceService) {
        this.accountService = accountService;
        this.queryService = queryService;
        this.archiveService = archiveService;
        this.balanceService = balanceService;
    }

    @GetMapping
    public PageResponse<AccountResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean includeArchived,
            @RequestParam(required = false) Boolean hasTransactions,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        AccountFilter filter = new AccountFilter(type, status, currencyCode, q, includeArchived, hasTransactions);
        return queryService.list(userId, page, size, sort, filter);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateAccountRequest request) {
        return queryService.detail(userId, accountService.create(userId, request).getId());
    }

    @GetMapping("/summary")
    public AccountTotalsResponse summary(@CurrentUserId Long userId) {
        return balanceService.totals(userId);
    }

    @GetMapping("/{id}")
    public AccountResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
        return queryService.detail(userId, id);
    }

    @PatchMapping("/{id}")
    public AccountResponse update(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest request) {
        return queryService.detail(userId, accountService.update(userId, id, request).getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        accountService.delete(userId, id, force);
    }

    @PostMapping("/{id}/archive")
    public AccountResponse archive(@CurrentUserId Long userId, @PathVariable Long id) {
        return queryService.detail(userId, archiveService.archive(userId, id).getId());
    }

    @PostMapping("/{id}/restore")
    public AccountResponse restore(@CurrentUserId Long userId, @PathVariable Long id) {
        return queryService.detail(userId, archiveService.restore(userId, id).getId());
    }

    @PostMapping("/{id}/set-primary")
    public AccountResponse setPrimary(@CurrentUserId Long userId, @PathVariable Long id) {
        return queryService.detail(userId, accountService.setPrimary(userId, id).getId());
    }

    @GetMapping("/{id}/balance")
    public AccountBalanceResponse balance(@CurrentUserId Long userId, @PathVariable Long id) {
        return balanceService.current(userId, id);
    }

    @GetMapping("/{id}/balances")
    public List<DailyBalancePointResponse> balances(@CurrentUserId Long userId, @PathVariable Long id,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return balanceService.dailyBalances(userId, id, from, to);
    }

    @PostMapping("/{id}/adjust")
    public AccountBalanceResponse adjust(@CurrentUserId Long userId, @PathVariable Long id,
            @Valid @RequestBody AdjustBalanceRequest request) {
        return balanceService.adjust(userId, id, request);
    }
}