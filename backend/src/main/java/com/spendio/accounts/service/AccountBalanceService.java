package com.spendio.accounts.service;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.request.AdjustBalanceRequest;
import com.spendio.accounts.dto.response.AccountBalanceResponse;
import com.spendio.accounts.dto.response.AccountTotalsResponse;
import com.spendio.accounts.dto.response.DailyBalancePointResponse;
import com.spendio.accounts.repository.AccountBalanceRepository;
import com.spendio.accounts.support.AccountOwnershipValidator;
import com.spendio.accounts.support.BalanceLine;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.ledger.AdjustmentPort;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Balance reads and the ADJUSTMENT write (ARCHITECTURE.md D.6, E.3). Balance maths runs
 * over the native ledger fragments: balance = opening balance + signed, non-deleted,
 * non-VOID, non-opening rows. The adjustment itself is written through the
 * {@link AdjustmentPort} into the transactions module (D.8), which resolves its FX
 * freeze; cross-currency read conversions go through the fx port - same-currency is
 * identity and any real conversion without a rate fails fast with FX_RATE_UNAVAILABLE.
 */
@Service
public class AccountBalanceService {

    private final AccountOwnershipValidator ownership;
    private final AccountBalanceRepository ledger;
    private final AdjustmentPort adjustment;
    private final BaseCurrencyPort baseCurrencyPort;
    private final ObjectProvider<FxConversionPort> fxConversion;

    public AccountBalanceService(AccountOwnershipValidator ownership, AccountBalanceRepository ledger,
            AdjustmentPort adjustment, BaseCurrencyPort baseCurrencyPort,
            ObjectProvider<FxConversionPort> fxConversion) {
        this.ownership = ownership;
        this.ledger = ledger;
        this.adjustment = adjustment;
        this.baseCurrencyPort = baseCurrencyPort;
        this.fxConversion = fxConversion;
    }

    public String baseCurrencyCode(Long userId) {
        return baseCurrencyPort.baseCurrencyCode(userId);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse current(Long userId, Long accountId) {
        Account account = ownership.requireOwned(userId, accountId);
        BalanceLine line = ledger.balanceLine(userId, accountId);
        long balance = line == null ? account.getOpeningBalanceMinor() : line.balance();
        String base = baseCurrencyCode(userId);
        long baseBalance = toBase(userId, balance, account.getCurrencyCode());
        return new AccountBalanceResponse(accountId, account.getCurrencyCode(), balance, base, baseBalance,
                LocalDate.now(), availableCredit(account, balance));
    }

    @Transactional(readOnly = true)
    public AccountTotalsResponse totals(Long userId) {
        String base = baseCurrencyCode(userId);
        Map<AccountType, Long> byType = new EnumMap<>(AccountType.class);
        Map<String, Long> byCurrency = new LinkedHashMap<>();
        long total = 0;
        for (BalanceLine line : ledger.balanceLines(userId)) {
            long balance = line.balance();
            long baseBalance = toBase(userId, balance, line.currencyCode());
            total += baseBalance;
            byType.merge(line.type(), baseBalance, Long::sum);
            byCurrency.merge(line.currencyCode(), balance, Long::sum);
        }
        return new AccountTotalsResponse(total, base, byType, byCurrency);
    }

    @Transactional(readOnly = true)
    public List<DailyBalancePointResponse> dailyBalances(Long userId, Long accountId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from must not be after to")
                    .withProperty("from", from).withProperty("to", to);
        }
        Account account = ownership.requireOwned(userId, accountId);
        String base = baseCurrencyCode(userId);
        Map<LocalDate, Long> nets = ledger.dailyNets(userId, accountId, from, to).stream()
                .collect(Collectors.toMap(f -> f.date(), f -> f.netMinor()));
        long balance = account.getOpeningBalanceMinor() + ledger.netBefore(userId, accountId, from);
        List<DailyBalancePointResponse> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            balance += nets.getOrDefault(day, 0L);
            points.add(new DailyBalancePointResponse(day, balance,
                    toBase(userId, balance, account.getCurrencyCode())));
        }
        return points;
    }

    @Transactional
    public AccountBalanceResponse adjust(Long userId, Long accountId, AdjustBalanceRequest request) {
        Account account = ownership.requireOwned(userId, accountId);
        if (account.isArchived()) {
            throw new BusinessException(ErrorCode.ACCOUNT_ARCHIVED,
                    "Archived accounts are frozen and cannot be adjusted")
                    .withProperty("accountId", accountId);
        }
        BalanceLine line = ledger.balanceLine(userId, accountId);
        long current = line == null ? account.getOpeningBalanceMinor() : line.balance();
        long delta = request.newBalanceMinor() - current;
        if (delta != 0) {
            adjustment.adjust(
                    userId, accountId,
                    delta > 0 ? TransactionDirection.INFLOW : TransactionDirection.OUTFLOW,
                    Math.abs(delta),
                    account.getCurrencyCode(),
                    request.occurredOn(),
                    request.reason());
        }
        return current(userId, accountId);
    }

    /** Amount in minor units of the user's base currency; identity when already there. */
    public long toBase(Long userId, long amountMinor, String currencyCode) {
        return toBase(userId, amountMinor, currencyCode, LocalDate.now());
    }

    /** Like {@link #toBase(Long, long, String)} but frozen on a specific business date. */
    public long toBase(Long userId, long amountMinor, String currencyCode, LocalDate onDate) {
        if (amountMinor == 0) {
            return 0;
        }
        String base = baseCurrencyCode(userId);
        if (currencyCode.equalsIgnoreCase(base)) {
            return amountMinor;
        }
        FxConversionPort port = fxConversion.getIfAvailable();
        if (port == null) {
            throw new BusinessException(ErrorCode.FX_RATE_UNAVAILABLE,
                    "Cannot convert " + currencyCode + " to " + base + " - fx support is not available")
                    .withProperty("fromCurrency", currencyCode)
                    .withProperty("toCurrency", base);
        }
        return port.convert(amountMinor, currencyCode, base, onDate).targetMinor();
    }

    private static Long availableCredit(Account account, long balanceMinor) {
        if (account.getType() != AccountType.CREDIT_CARD || account.getCreditLimitMinor() == null) {
            return null;
        }
        long borrowed = Math.max(0, -balanceMinor);
        return account.getCreditLimitMinor() - borrowed;
    }
}