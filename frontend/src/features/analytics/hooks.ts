import { useQuery } from '@tanstack/react-query'
import { analyticsApi, type AnalyticsRange } from './api'

export const analyticsKeys = {
  all: ['analytics'] as const,
  cashFlow: (range: AnalyticsRange) => ['analytics', 'cash-flow', range] as const,
  spendingTrend: (range: AnalyticsRange) => ['analytics', 'spending-trend', range] as const,
  incomeVsExpense: (range: AnalyticsRange) => ['analytics', 'income-vs-expense', range] as const,
  categoryBreakdown: (range: AnalyticsRange, type: string, limit?: number, parentOnly?: boolean) =>
    ['analytics', 'category-breakdown', range, type, limit ?? 0, parentOnly ?? false] as const,
  topSpending: (range: AnalyticsRange, limit?: number) =>
    ['analytics', 'top-spending', range, limit ?? 0] as const,
  monthlyComparison: (year?: number) => ['analytics', 'monthly-comparison', year ?? 0] as const,
  budgetUtilization: ['analytics', 'budget-utilization'] as const,
  goalProgress: ['analytics', 'goal-progress'] as const,
  netWorth: (range: AnalyticsRange) => ['analytics', 'net-worth', range] as const,
  accounts: ['analytics', 'accounts'] as const,
  dashboardSummary: (month?: string) => ['dashboard', 'summary', month ?? ''] as const,
  recentTransactions: (limit: number) => ['dashboard', 'recent', limit] as const,
  upcomingRecurring: (days: number) => ['dashboard', 'upcoming', days] as const,
}

export function useCashFlow(range: AnalyticsRange) {
  return useQuery({
    queryKey: analyticsKeys.cashFlow(range),
    queryFn: () => analyticsApi.cashFlow(range),
  })
}

export function useSpendingTrend(range: AnalyticsRange) {
  return useQuery({
    queryKey: analyticsKeys.spendingTrend(range),
    queryFn: () => analyticsApi.spendingTrend(range),
  })
}

export function useIncomeVsExpense(range: AnalyticsRange) {
  return useQuery({
    queryKey: analyticsKeys.incomeVsExpense(range),
    queryFn: () => analyticsApi.incomeVsExpense(range),
  })
}

export function useCategoryBreakdown(
  range: AnalyticsRange,
  type: 'INCOME' | 'EXPENSE',
  limit?: number,
  parentOnly?: boolean,
) {
  return useQuery({
    queryKey: analyticsKeys.categoryBreakdown(range, type, limit, parentOnly),
    queryFn: () => analyticsApi.categoryBreakdown(range, type, limit, parentOnly),
  })
}

export function useTopSpending(range: AnalyticsRange, limit?: number) {
  return useQuery({
    queryKey: analyticsKeys.topSpending(range, limit),
    queryFn: () => analyticsApi.topSpending(range, limit),
  })
}

export function useMonthlyComparison(year?: number) {
  return useQuery({
    queryKey: analyticsKeys.monthlyComparison(year),
    queryFn: () => analyticsApi.monthlyComparison(year),
  })
}

export function useBudgetUtilization() {
  return useQuery({
    queryKey: analyticsKeys.budgetUtilization,
    queryFn: () => analyticsApi.budgetUtilization(),
  })
}

export function useGoalProgress() {
  return useQuery({
    queryKey: analyticsKeys.goalProgress,
    queryFn: () => analyticsApi.goalProgress(),
  })
}

export function useNetWorth(range: AnalyticsRange) {
  return useQuery({
    queryKey: analyticsKeys.netWorth(range),
    queryFn: () => analyticsApi.netWorth(range),
  })
}

export function useAccountBreakdown() {
  return useQuery({
    queryKey: analyticsKeys.accounts,
    queryFn: () => analyticsApi.accounts(),
  })
}

export function useDashboardSummary(month?: string) {
  return useQuery({
    queryKey: analyticsKeys.dashboardSummary(month),
    queryFn: () => analyticsApi.dashboardSummary(month),
  })
}

export function useRecentTransactions(limit = 5) {
  return useQuery({
    queryKey: analyticsKeys.recentTransactions(limit),
    queryFn: () => analyticsApi.recentTransactions(limit),
  })
}

export function useDashboardUpcomingRecurring(days = 30) {
  return useQuery({
    queryKey: analyticsKeys.upcomingRecurring(days),
    queryFn: () => analyticsApi.upcomingRecurring(days),
  })
}