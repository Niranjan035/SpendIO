import { http, type QueryValue } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type { Transaction } from '@/features/transactions/types'
import type { RecurringTransaction } from '@/features/recurring/types'
import type {
  AccountBreakdown,
  BudgetUtilizationItem,
  CashFlowPoint,
  CategoryBreakdownItem,
  DashboardSummary,
  AnalyticsGranularity,
  GoalProgressItem,
  MonthlyComparisonItem,
  NetWorthPoint,
  SavingsRatePoint,
  SpendingTrendPoint,
  TopSpendingItem,
} from './types'

export interface AnalyticsRange {
  from: string
  to: string
  granularity?: AnalyticsGranularity
}

function rangeQuery(range?: AnalyticsRange): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {}
  if (range?.from) query.from = range.from
  if (range?.to) query.to = range.to
  if (range?.granularity) query.granularity = range.granularity
  return query
}

export const analyticsApi = {
  cashFlow(range?: AnalyticsRange): Promise<CashFlowPoint[]> {
    return http.get<CashFlowPoint[]>(endpoints.analytics.cashFlow, { query: rangeQuery(range) })
  },

  spendingTrend(range?: AnalyticsRange): Promise<SpendingTrendPoint[]> {
    return http.get<SpendingTrendPoint[]>(endpoints.analytics.spendingTrend, {
      query: rangeQuery(range),
    })
  },

  incomeVsExpense(range?: AnalyticsRange): Promise<SavingsRatePoint[]> {
    return http.get<SavingsRatePoint[]>(endpoints.analytics.incomeVsExpense, {
      query: rangeQuery(range),
    })
  },

  categoryBreakdown(
    range: AnalyticsRange,
    type: 'INCOME' | 'EXPENSE',
    limit?: number,
    parentOnly?: boolean,
  ): Promise<CategoryBreakdownItem[]> {
    return http.get<CategoryBreakdownItem[]>(endpoints.analytics.categoryBreakdown, {
      query: {
        ...rangeQuery(range),
        type,
        limit,
        parentOnly,
      },
    })
  },

  topSpending(range: AnalyticsRange, limit?: number): Promise<TopSpendingItem[]> {
    return http.get<TopSpendingItem[]>(endpoints.analytics.topSpending, {
      query: { ...rangeQuery(range), limit },
    })
  },

  monthlyComparison(year?: number): Promise<MonthlyComparisonItem[]> {
    return http.get<MonthlyComparisonItem[]>(endpoints.analytics.monthlyComparison, {
      query: { year },
    })
  },

  budgetUtilization(): Promise<BudgetUtilizationItem[]> {
    return http.get<BudgetUtilizationItem[]>(endpoints.analytics.budgetUtilization)
  },

  goalProgress(): Promise<GoalProgressItem[]> {
    return http.get<GoalProgressItem[]>(endpoints.analytics.goalProgress)
  },

  netWorth(range?: AnalyticsRange): Promise<NetWorthPoint[]> {
    return http.get<NetWorthPoint[]>(endpoints.analytics.netWorth, {
      query: { from: range?.from, to: range?.to },
    })
  },

  accounts(): Promise<AccountBreakdown> {
    return http.get<AccountBreakdown>(endpoints.analytics.accounts)
  },

  dashboardSummary(month?: string): Promise<DashboardSummary> {
    return http.get<DashboardSummary>(endpoints.dashboard.summary, { query: { month } })
  },

  recentTransactions(limit = 5): Promise<Transaction[]> {
    return http.get<Transaction[]>(endpoints.dashboard.recentTransactions, { query: { limit } })
  },

  upcomingRecurring(days = 30): Promise<RecurringTransaction[]> {
    return http.get<RecurringTransaction[]>(endpoints.dashboard.upcomingRecurring, {
      query: { days },
    })
  },
}