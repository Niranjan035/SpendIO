import type { BudgetPeriod, BudgetStatus, PaceStatus } from '@/types/enums'
import type { PageResponse } from '@/types/pagination'

export interface BudgetCategoryRef {
  id: number
  name: string
  type: 'INCOME' | 'EXPENSE'
  color: string | null
  icon: string | null
}

/** Budget read model mirroring BudgetResponse (ARCHITECTURE.md D.10). */
export interface Budget {
  id: number
  name: string
  category: BudgetCategoryRef | null
  periodType: BudgetPeriod
  periodStart: string
  periodEnd: string
  amountMinor: number
  currencyCode: string
  baseAmountMinor: number
  rolloverEnabled: boolean
  rolloverAmountMinor: number
  alertThresholdPct: number
  alertEnabled: boolean
  includeSubcategories: boolean
  status: BudgetStatus
  version: number
  createdAt: string
}

export type BudgetPage = PageResponse<Budget>

export interface BudgetFilter {
  status?: BudgetStatus
  periodType?: BudgetPeriod
  categoryId?: number
  activeInPeriod?: string
  page?: number
  size?: number
}

/** One category's contribution to the spent total, with its share (D.10). */
export interface BudgetCategorySlice {
  categoryId: number
  name: string
  color: string | null
  spentMinor: number
  sharePct: number
}

/** Live progress in one period window, figures in the budget's currency. */
export interface BudgetProgress {
  budget: Budget
  period: {
    start: string
    end: string
    type: BudgetPeriod
  }
  allowanceMinor: number
  spentMinor: number
  remainingMinor: number
  utilisationPct: number
  projectedSpendMinor: number
  dailyAllowanceMinor: number
  overBudget: boolean
  paceStatus: PaceStatus
  daysElapsed: number
  daysRemaining: number
  categoryBreakdown: BudgetCategorySlice[]
}

/** One threshold crossing of a budget (D.10, E.10). */
export interface BudgetAlert {
  id: number
  budgetId: number
  budgetName: string
  currencyCode: string
  periodStart: string
  periodEnd: string
  thresholdPct: number
  budgetAmountMinor: number
  spentAmountMinor: number
  triggeredAt: string
  acknowledgedAt: string | null
  acknowledged: boolean
}

export type BudgetAlertPage = PageResponse<BudgetAlert>

export interface BudgetAlertFilter {
  unacknowledgedOnly?: boolean
  from?: string
  to?: string
  page?: number
  size?: number
}

/** Historical utilisation for one budget, oldest window first (K.5). */
export interface BudgetHistory {
  budgetId: number
  periods: BudgetHistoryPeriod[]
}

export interface BudgetHistoryPeriod {
  periodStart: string
  periodEnd: string
  allowanceMinor: number
  spentMinor: number
  utilisationPct: number
  overBudget: boolean
  current: boolean
}

export interface CreateBudgetInput {
  name: string
  categoryId: number | null
  periodType: BudgetPeriod
  periodStart: string
  periodEnd: string
  amountMinor: number
  currencyCode: string
  rolloverEnabled: boolean
  alertThresholdPct: number
  alertEnabled: boolean
  includeSubcategories: boolean
}

export interface UpdateBudgetInput {
  name?: string
  categoryId?: number
  periodType?: BudgetPeriod
  periodStart?: string
  periodEnd?: string
  amountMinor?: number
  currencyCode?: string
  rolloverEnabled?: boolean
  alertThresholdPct?: number
  alertEnabled?: boolean
  includeSubcategories?: boolean
  status?: BudgetStatus
  version?: number
}

export interface EvaluateResult {
  created: number
}