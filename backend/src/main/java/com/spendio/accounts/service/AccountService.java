package com.spendio.accounts.service;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.dto.request.UpdateAccountRequest;
import com.spendio.accounts.repository.AccountBalanceRepository;
import com.spendio.accounts.repository.AccountRepository;
import com.spendio.accounts.support.AccountOwnershipValidator;
import com.spendio.reference.service.CurrencyService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle writes (ARCHITECTURE.md D.6): create, update, delete (soft, with
 * optional ledger cascade) and primary selection. Currency and opening balance are
 * immutable after creation - currency additionally stays immutable once any ledger row
 * exists.
 */
@Service
public class AccountService {

    private static final int NAME_MAX = 80;

    private final AccountRepository accounts;
    private final AccountBalanceRepository ledger;
    private final AccountOwnershipValidator ownership;
    private final CurrencyService currencyService;
    private final AccountBalanceService balanceService;

    public AccountService(AccountRepository accounts, AccountBalanceRepository ledger,
            AccountOwnershipValidator ownership, CurrencyService currencyService,
            AccountBalanceService balanceService) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.ownership = ownership;
        this.currencyService = currencyService;
        this.balanceService = balanceService;
    }

    @Transactional
    public Account create(Long userId, CreateAccountRequest request) {
        currencyService.requireSupported(request.currencyCode());
        String name = request.name().trim();
        if (accounts.existsByUserIdAndNameAndDeletedAtIsNull(userId, name)) {
            throw duplicateName(name);
        }
        if (request.primary()) {
            accounts.findFirstByUserIdAndPrimaryAccountTrueAndDeletedAtIsNull(userId)
                    .ifPresent(existing -> {
                        throw new BusinessException(ErrorCode.ACCOUNT_PRIMARY_ALREADY_EXISTS,
                                "This user already has a primary account")
                                .withProperty("accountId", existing.getId());
                    });
        }
        Account account = Account.create(userId, name, request.type(),
                request.currencyCode().trim().toUpperCase(java.util.Locale.ROOT),
                request.openingBalanceMinor(), request.openingBalanceDate());
        apply(account, request.institution(), request.last4(), request.creditLimitMinor(),
                request.billingDay(), request.dueDay(), request.icon(), request.color(), request.notes());
        account.setPrimary(request.primary());
        Account saved = accounts.save(account);

        if (request.createOpeningBalanceTransaction() && saved.getOpeningBalanceMinor() != 0) {
            LocalDate openingTransactionDate = request.openingTransactionDate() != null
                    ? request.openingTransactionDate()
                    : saved.getOpeningBalanceDate();
            ledger.insertAdjustment(
                    userId, saved.getId(),
                    saved.getOpeningBalanceMinor() >= 0 ? "INFLOW" : "OUTFLOW",
                    Math.abs(saved.getOpeningBalanceMinor()),
                    saved.getCurrencyCode(),
                    balanceService.toBase(userId, saved.getOpeningBalanceMinor(),
                            saved.getCurrencyCode(), openingTransactionDate),
                    openingTransactionDate,
                    "Opening balance",
                    "opening:" + saved.getId());
        }
        return saved;
    }

    @Transactional
    public Account update(Long userId, Long accountId, UpdateAccountRequest request) {
        Account account = ownership.requireOwned(userId, accountId);
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().trim();
            if (!name.equals(account.getName())
                    && accounts.existsByUserIdAndNameAndDeletedAtIsNullAndIdNot(userId, name, accountId)) {
                throw duplicateName(name);
            }
            account.rename(name);
        }
        if (request.type() != null) {
            account.changeType(request.type());
        }
        if (request.currencyCode() != null && !request.currencyCode().isBlank()
                && !request.currencyCode().equalsIgnoreCase(account.getCurrencyCode())) {
            String newCode = request.currencyCode().trim().toUpperCase(java.util.Locale.ROOT);
            if (ledger.countTransactions(userId, accountId) > 0) {
                throw new BusinessException(ErrorCode.ACCOUNT_CURRENCY_IMMUTABLE,
                        "The currency of an account with transactions cannot be changed")
                        .withProperty("accountId", accountId)
                        .withProperty("currencyCode", account.getCurrencyCode());
            }
            currencyService.requireSupported(newCode);
            account.changeCurrencyCode(newCode);
        }
        apply(account, request.institution(), request.last4(), request.creditLimitMinor(),
                request.billingDay(), request.dueDay(), request.icon(), request.color(), request.notes());
        return accounts.save(account);
    }

    @Transactional
    public void delete(Long userId, Long accountId, boolean force) {
        Account account = ownership.requireOwned(userId, accountId);
        if (!force && ledger.countTransactions(userId, accountId) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_HAS_TRANSACTIONS,
                    "This account still has transactions; delete with force=true to soft-delete them together")
                    .withProperty("accountId", accountId);
        }
        if (force) {
            ledger.softDeleteTransactionsFor(userId, accountId);
        }
        account.rename(archivedName(account.getName()));
        account.softDelete();
        accounts.save(account);
    }

    @Transactional
    public Account setPrimary(Long userId, Long accountId) {
        Account account = ownership.requireOwned(userId, accountId);
        if (account.isArchived()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ARCHIVED,
                    "An archived account cannot become the primary account")
                    .withProperty("accountId", accountId);
        }
        accounts.demoteAllOtherPrimaries(userId, accountId);
        account.setPrimary(true);
        return accounts.save(account);
    }

    private static void apply(Account account, String institution, String last4, Long creditLimitMinor,
            Integer billingDay, Integer dueDay, String icon, String color, String notes) {
        if (institution != null) {
            account.setInstitution(institution);
        }
        if (last4 != null) {
            account.setLast4(last4);
        }
        if (creditLimitMinor != null) {
            account.setCreditLimitMinor(creditLimitMinor);
        }
        if (billingDay != null) {
            account.setBillingDay(billingDay);
        }
        if (dueDay != null) {
            account.setDueDay(dueDay);
        }
        if (icon != null) {
            account.setIcon(icon);
        }
        if (color != null) {
            account.setColor(color);
        }
        if (notes != null) {
            account.setNotes(notes);
        }
    }

    private static String archivedName(String name) {
        String suffix = " (archived " + YearMonth.now() + ")";
        int room = NAME_MAX - suffix.length();
        String trimmed = name.length() > room ? name.substring(0, room) : name;
        return trimmed + suffix;
    }

    private static BusinessException duplicateName(String name) {
        return new BusinessException(ErrorCode.ACCOUNT_NAME_DUPLICATE,
                "This user already has an account named '" + name + "'")
                .withFieldError("name", "must be unique for the user");
    }
}