package com.spendio.goals.service;

import com.spendio.goals.domain.Goal;
import com.spendio.goals.domain.GoalContribution;
import com.spendio.goals.dto.response.GoalContributionResponse;
import com.spendio.goals.dto.response.GoalProgressResponse;
import com.spendio.goals.dto.response.GoalResponse;
import com.spendio.goals.dto.response.GoalSummaryResponse;
import com.spendio.goals.mapper.GoalMapper;
import com.spendio.goals.repository.GoalContributionRepository;
import com.spendio.goals.repository.GoalRepository;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.sharedkernel.web.PageRequestFactory;
import com.spendio.sharedkernel.web.PageResponse;
import com.spendio.transactions.support.LedgerFx;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Goal reads (ARCHITECTURE.md D.11/E.11): list with status/name filters, the
 * dashboard summary (totals converted to the base currency), contribution history and
 * the progress view - cumulative chart, milestone crossings and the required-per-month
 * figures that {@link GoalMapper} derives per goal.
 *
 * <p>Milestones are derived from contribution history rather than a "notified" flag:
 * the first contribution whose running total reaches a threshold is its crossing
 * point, so the answer is stable across restarts and is exactly what
 * {@link GoalMilestoneNotifier} used to fire the event once.
 */
@Service
public class GoalQueryService {

    private static final int RECENT_CONTRIBUTIONS = 100;
    private static final int MAX_CHART_POINTS = 400;

    /** Chart bucket sizes, coarsest last - the builder picks the finest that fits the cap. */
    public enum Granularity { DAY, WEEK, MONTH, YEAR }

    private final GoalRepository goals;
    private final GoalContributionRepository contributions;
    private final GoalMapper mapper;
    private final BaseCurrencyPort baseCurrency;
    private final ObjectProvider<FxConversionPort> fxPort;

    public GoalQueryService(GoalRepository goals, GoalContributionRepository contributions, GoalMapper mapper,
            BaseCurrencyPort baseCurrency, ObjectProvider<FxConversionPort> fxPort) {
        this.goals = goals;
        this.contributions = contributions;
        this.mapper = mapper;
        this.baseCurrency = baseCurrency;
        this.fxPort = fxPort;
    }

    @Transactional(readOnly = true)
    public PageResponse<GoalResponse> list(Long userId, GoalStatus status, String q,
            Integer page, Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size,
                Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<Goal> result = goals.search(userId, status, blankToNull(q), pageable);
        return PageResponse.from(result.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public GoalResponse detail(Long userId, Long id) {
        return mapper.toResponse(requireOwned(userId, id));
    }

    @Transactional(readOnly = true)
    public GoalSummaryResponse summary(Long userId) {
        List<Goal> all = goals.search(userId, null, null,
                PageRequestFactory.of(0, 1000, Sort.by(Sort.Direction.DESC, "priority"))).getContent();
        String base = baseCurrency.baseCurrencyCode(userId);
        FxConversionPort fx = fxPort.getIfAvailable();
        LocalDate today = LocalDate.now();

        long totalTarget = 0;
        long totalCurrent = 0;
        int active = 0;
        int completed = 0;
        for (Goal goal : all) {
            if (goal.getStatus() == GoalStatus.ACTIVE) {
                active++;
            } else if (goal.getStatus() == GoalStatus.COMPLETED) {
                completed++;
            }
            totalTarget += toBase(fx, goal.getTargetAmountMinor(), goal.getCurrencyCode(), base, today);
            totalCurrent += toBase(fx, goal.getCurrentAmountMinor(), goal.getCurrencyCode(), base, today);
        }
        return new GoalSummaryResponse(
                active,
                completed,
                totalTarget,
                totalCurrent,
                Math.max(0, totalTarget - totalCurrent),
                GoalMapper.progressPct(totalCurrent, totalTarget),
                base,
                all.stream().map(mapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public GoalProgressResponse progress(Long userId, Long id, Granularity granularity) {
        Goal goal = requireOwned(userId, id);
        List<GoalContribution> history = contributions.findByGoalIdOrderByContributedOnAscIdAsc(goal.getId());
        List<GoalContributionResponse> recent = contributions
                .findByGoalIdOrderByContributedOnDescIdDesc(goal.getId(),
                        PageRequestFactory.of(0, RECENT_CONTRIBUTIONS, Sort.by(Sort.Direction.DESC, "id")))
                .map(mapper::toContributionResponse)
                .getContent();
        return new GoalProgressResponse(
                mapper.toResponse(goal),
                recent,
                chart(goal, history, granularity == null ? Granularity.MONTH : granularity),
                milestones(goal, history));
    }

    @Transactional(readOnly = true)
    public PageResponse<GoalContributionResponse> contributionHistory(Long userId, Long goalId,
            Integer page, Integer size) {
        requireOwned(userId, goalId);
        Pageable pageable = PageRequestFactory.of(page, size,
                Sort.by(Sort.Direction.DESC, "contributedOn").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<GoalContribution> result =
                contributions.findByGoalIdOrderByContributedOnDescIdDesc(goalId, pageable);
        return PageResponse.from(result.map(mapper::toContributionResponse));
    }

    /**
     * Cumulative series from the first contribution (or goal creation) through today,
     * one point per bucket including empty ones so the chart has no gaps. A series
     * larger than {@value MAX_CHART_POINTS} points coarsens DAY → WEEK → MONTH → YEAR
     * until it fits.
     */
    private List<GoalProgressResponse.ChartPoint> chart(Goal goal, List<GoalContribution> history,
            Granularity requested) {
        Granularity granularity = requested;
        LocalDate first = history.isEmpty()
                ? (goal.getCreatedAt() == null ? LocalDate.now()
                        : LocalDate.ofInstant(goal.getCreatedAt(), ZoneOffset.UTC))
                : history.get(0).getContributedOn();
        LocalDate to = LocalDate.now();
        LocalDate from = first.isAfter(to) ? to : first;
        while (pointsBetween(from, to, granularity) > MAX_CHART_POINTS && granularity != Granularity.YEAR) {
            granularity = coarser(granularity);
        }

        // Seed every bucket from the (possibly coarsened) start so empty periods keep
        // the series continuous; contributions outside the seed range cannot exist.
        Map<LocalDate, Long> added = new TreeMap<>();
        LocalDate seed = bucketStart(from, granularity);
        for (LocalDate day = seed; !day.isAfter(to); day = advance(day, granularity)) {
            added.put(day, 0L);
        }
        for (GoalContribution contribution : history) {
            LocalDate bucket = bucketStart(contribution.getContributedOn(), granularity);
            if (added.containsKey(bucket)) {
                added.merge(bucket, contribution.getAmountMinor(), Long::sum);
            }
        }

        List<GoalProgressResponse.ChartPoint> points = new ArrayList<>(added.size());
        long cumulative = 0;
        for (Map.Entry<LocalDate, Long> entry : added.entrySet()) {
            cumulative += entry.getValue();
            points.add(new GoalProgressResponse.ChartPoint(entry.getKey(), entry.getValue(), cumulative));
        }
        return points;
    }

    /**
     * Standard 25/50/75 rungs plus the goal's configured threshold and completion,
     * each with the first date history reached it (null = still ahead).
     */
    private List<GoalProgressResponse.MilestoneStatus> milestones(Goal goal,
            List<GoalContribution> history) {
        TreeSet<Short> pcts = new TreeSet<>(List.of((short) 25, (short) 50, (short) 75, (short) 100));
        pcts.add(goal.getMilestonePct());

        Map<Short, Long> amounts = new TreeMap<>();
        for (Short pct : pcts) {
            amounts.put(pct, GoalMilestoneNotifier.ceilPct(goal.getTargetAmountMinor(), pct));
        }

        Map<Short, LocalDate> crossedOn = new TreeMap<>();
        long cumulative = 0;
        for (GoalContribution contribution : history) {
            cumulative += contribution.getAmountMinor();
            for (Map.Entry<Short, Long> entry : amounts.entrySet()) {
                Short pct = entry.getKey();
                if (!crossedOn.containsKey(pct) && cumulative >= entry.getValue()) {
                    crossedOn.put(pct, contribution.getContributedOn());
                }
            }
        }

        List<GoalProgressResponse.MilestoneStatus> result = new ArrayList<>();
        for (Map.Entry<Short, Long> entry : amounts.entrySet()) {
            Short pct = entry.getKey();
            LocalDate crossedAt = crossedOn.get(pct);
            result.add(new GoalProgressResponse.MilestoneStatus(pct, entry.getValue(), crossedAt,
                    crossedAt != null));
        }
        return result;
    }

    private static LocalDate bucketStart(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(java.time.DayOfWeek.MONDAY);
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    private static LocalDate advance(LocalDate day, Granularity granularity) {
        return switch (granularity) {
            case DAY -> day.plusDays(1);
            case WEEK -> day.plusWeeks(1);
            case MONTH -> day.plusMonths(1);
            case YEAR -> day.plusYears(1);
        };
    }

    private static long pointsBetween(LocalDate from, LocalDate to, Granularity granularity) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return switch (granularity) {
            case DAY -> days;
            case WEEK -> days / 7 + 1;
            case MONTH -> ChronoUnit.MONTHS.between(from, to) + 1;
            case YEAR -> ChronoUnit.YEARS.between(from, to) + 1;
        };
    }

    private static Granularity coarser(Granularity granularity) {
        return switch (granularity) {
            case DAY -> Granularity.WEEK;
            case WEEK -> Granularity.MONTH;
            case MONTH, YEAR -> Granularity.YEAR;
        };
    }

    /**
     * Totals in the base currency; without an FX provider a foreign-currency goal
     * counts 1:1 (mirrors the read-side fallback in {@link GoalMapper}) instead of
     * failing the whole summary.
     */
    private long toBase(FxConversionPort fx, long amountMinor, String currency, String base, LocalDate today) {
        if (currency.equals(base) || fx == null) {
            return amountMinor;
        }
        return LedgerFx.convert(fx, amountMinor, currency, base, today);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Goal requireOwned(Long userId, Long id) {
        return goals.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOAL_NOT_FOUND, "Goal not found")
                        .withProperty("id", id));
    }
}
