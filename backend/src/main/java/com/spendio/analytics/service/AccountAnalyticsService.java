package com.spendio.analytics.service;

import com.spendio.accounts.service.AccountBalanceService;
import com.spendio.accounts.service.AccountSnapshotService;
import com.spendio.analytics.dto.response.AccountBreakdownResponse;
import com.spendio.analytics.dto.response.AccountBreakdownResponse.AccountPoint;
import com.spendio.analytics.dto.response.AccountBreakdownResponse.TypeTotal;
import com.spendio.analytics.dto.response.NetWorthPointResponse;
import com.spendio.analytics.dto.response.NetWorthPointResponse.ByAccount;
import com.spendio.analytics.mapper.AnalyticsMapper;
import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.analytics.repository.AnalyticsQueryRepository.AccountLine;
import com.spendio.analytics.repository.AnalyticsQueryRepository.SnapshotPoint;
import com.spendio.analytics.support.AnalyticsRangeValidator;
import com.spendio.analytics.support.PeriodBucket;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Balance views (ARCHITECTURE.md D.14): the account/type breakdown reads the live
 * ledger once and converts through the accounts service; the net-worth series reads
 * {@code account_snapshots} (after a read-through capture of today) so history never
 * re-sums the ledger.
 */
@Service
public class AccountAnalyticsService {

    private final AnalyticsQueryRepository query;
    private final AnalyticsMapper mapper;
    private final AnalyticsCacheManager cache;
    private final AccountBalanceService balances;
    private final AccountSnapshotService snapshots;
    private final BaseCurrencyPort baseCurrency;

    public AccountAnalyticsService(AnalyticsQueryRepository query, AnalyticsMapper mapper,
            AnalyticsCacheManager cache, AccountBalanceService balances,
            AccountSnapshotService snapshots, BaseCurrencyPort baseCurrency) {
        this.query = query;
        this.mapper = mapper;
        this.cache = cache;
        this.balances = balances;
        this.snapshots = snapshots;
        this.baseCurrency = baseCurrency;
    }

    public AccountBreakdownResponse accounts(Long userId) {
        return cache.get(userId, "accounts-breakdown", AccountBreakdownResponse.class,
                () -> buildBreakdown(userId));
    }

    public List<NetWorthPointResponse> netWorth(Long userId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusMonths(12).withDayOfMonth(1);
        AnalyticsRangeValidator.validate(start, end, PeriodBucket.MONTHLY);
        String key = "net-worth:" + start + ":" + end;
        return cache.get(userId, key, List.class, () -> buildNetWorth(userId, start, end));
    }

    private AccountBreakdownResponse buildBreakdown(Long userId) {
        String base = baseCurrency.baseCurrencyCode(userId);
        List<AccountLine> lines = query.accountLines(userId);

        List<AccountPoint> points = new ArrayList<>();
        Map<AccountType, Long> byType = new EnumMap<>(AccountType.class);
        long total = 0;
        for (AccountLine line : lines) {
            long balance = line.openingBalanceMinor() + line.netMinor();
            long baseBalance = balances.toBase(userId, balance, line.currencyCode());
            total += baseBalance;
            byType.merge(line.type(), baseBalance, Long::sum);
            points.add(new AccountPoint(
                    line.accountId(), line.name(), line.type(), line.currencyCode(),
                    balance, baseBalance, BigDecimal.ZERO));
        }

        List<AccountPoint> withShare = new ArrayList<>();
        for (AccountPoint point : points) {
            withShare.add(new AccountPoint(
                    point.accountId(), point.name(), point.type(), point.currencyCode(),
                    point.balanceMinor(), point.balanceBaseMinor(),
                    mapper.sharePct(point.balanceBaseMinor(), total)));
        }

        List<TypeTotal> typeTotals = new ArrayList<>();
        for (AccountType type : AccountType.values()) {
            Long value = byType.get(type);
            if (value != null) {
                typeTotals.add(new TypeTotal(type, value));
            }
        }
        return new AccountBreakdownResponse(total, base, withShare, typeTotals);
    }

    private List<NetWorthPointResponse> buildNetWorth(Long userId, LocalDate start, LocalDate end) {
        snapshots.ensureToday(userId);
        Map<LocalDate, List<SnapshotPoint>> byDate = new LinkedHashMap<>();
        for (SnapshotPoint point : query.snapshots(userId, start, end)) {
            byDate.computeIfAbsent(point.snapshotDate(), k -> new ArrayList<>()).add(point);
        }
        List<NetWorthPointResponse> series = new ArrayList<>();
        for (Map.Entry<LocalDate, List<SnapshotPoint>> entry : byDate.entrySet()) {
            List<ByAccount> byAccount = new ArrayList<>();
            long total = 0;
            for (SnapshotPoint point : entry.getValue()) {
                total += point.balanceBaseMinor();
                byAccount.add(new ByAccount(
                        point.accountId(), point.accountName(), point.accountType(),
                        point.balanceBaseMinor()));
            }
            series.add(new NetWorthPointResponse(entry.getKey(), total, byAccount));
        }
        return series;
    }
}
