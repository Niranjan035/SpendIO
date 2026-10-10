import type { AccountType } from '@/types/enums'

/**
 * Analytics & dashboard response contracts (ARCHITECTURE.md D.14, E.14).
 * All money figures are base-currency minor units unless a field says otherwise.
 */

export interface CashFlowPoint {
  period: string
  incomeMinor: number
  expenseMinor: number
  netMinor: number
}

export interface SpendingTrendPoint {
  period: string
  expenseMinor: number
  budgetMinor: number
  cumulativeMinor: number
}

export interface SavingsRatePoint {
  period: string
  incomeMinor: number
  expenseMinor: number
  savingsMinor: number
  savingsRatePct: number | null
}

export interface CategoryBreakdownItem {
  categoryId: number | null
  name: string | null
  color: string | null
  amountMinor: number
  sharePct: number
  transactionCount: number
}

export interface TopSpendingItem {
  rank: number
  categoryId: number | null
  name: string | null
  amountMinor: number
  sharePct: number
  deltaPct: number | null
}

export interface MonthlyComparisonItem {
  month: string
  incomeMinor: number
  expenseMinor: number
  netMinor: number
  vsPreviousMonthPct: number | null
}

export interface BudgetUtilizationItem {
  budgetId: number
  name: string
  spentMinor: number
  amountMinor: number
  utilisationPct: number
  remainingMinor: number
  projectedMinor: number
  status: 'ON_TRACK' | 'AT_RISK' | 'OVER_BUDGET'
  /** Budgets keep their own currency (D.10), so figures are labelled per item. */
  currencyCode: string
}

export interface GoalProgressItem {
  goalId: number
  name: string
  targetMinor: number
  currentMinor: number
  progressPct: number
  onTrack: boolean
  /** Goals keep their own currency, so figures are labelled per item. */
  currencyCode: string
}

export interface NetWorthPoint {
  date: string
  totalMinor: number
  byAccount: NetWorthByAccount[]
}

export interface NetWorthByAccount {
  accountId: number | null
  name: string
  type: AccountType
  balanceBaseMinor: number
}

export interface AccountBreakdown {
  totalBaseMinor: number
  baseCurrencyCode: string
  accounts: AccountPoint[]
  byType: TypeTotal[]
}

export interface AccountPoint {
  accountId: number
  name: string
  type: AccountType
  currencyCode: string
  balanceMinor: number
  balanceBaseMinor: number
  sharePct: number | null
}

export interface TypeTotal {
  type: AccountType
  totalBaseMinor: number
}

export interface DashboardSummary {
  totalBalanceBaseMinor: number
  baseCurrencyCode: string
  incomeBaseMinor: number
  expenseBaseMinor: number
  savingsBaseMinor: number
  /** Null when there was no income; omitted from the wire by the NON_NULL policy. */
  savingsRatePct?: number | null
  transactionCount: number
  budgetUtilisationPct: number | null
  goalProgressPct: number | null
  periodStart: string
  periodEnd: string
  compare: DashboardCompare
}

export interface DashboardCompare {
  previousIncome: number
  previousExpense: number
  previousSavings: number
  deltas: DashboardDeltas
}

/**
 * Percent change vs the previous period; null when the previous period was zero.
 * The backend omits null fields (Jackson NON_NULL), so each value may be absent
 * as well as explicitly null — always treat `null`/`undefined` as "no comparison".
 */
export interface DashboardDeltas {
  incomePct?: number | null
  expensePct?: number | null
  savingsPct?: number | null
}

/** Wire value for the backend granularity parameter. */
export type AnalyticsGranularity = 'daily' | 'weekly' | 'monthly'