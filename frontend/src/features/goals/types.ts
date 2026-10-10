import type { GoalContributionMethod, GoalStatus } from '@/types/enums'
import type { PageResponse } from '@/types/pagination'

/** Savings goal read model mirroring GoalResponse (ARCHITECTURE.md D.11). */
export interface Goal {
  id: number
  name: string
  description: string | null
  targetAmountMinor: number
  currentAmountMinor: number
  remainingMinor: number
  progressPct: number
  currencyCode: string
  baseProgressPct: number
  targetDate: string | null
  daysRemaining: number | null
  status: GoalStatus
  linkedAccountId: number | null
  /** 0 (lowest) … 5 (highest). */
  priority: number
  icon: string | null
  color: string | null
  completedAt: string | null
  monthlyRequiredMinor: number | null
  onTrack: boolean | null
  version: number
  createdAt: string
}

export type GoalPage = PageResponse<Goal>

export interface GoalFilter {
  status?: GoalStatus
  q?: string
  page?: number
  size?: number
}

/** Aggregate progress in the user's base currency (GET /goals/summary). */
export interface GoalSummary {
  activeCount: number
  completedCount: number
  totalTargetBaseMinor: number
  totalCurrentBaseMinor: number
  remainingBaseMinor: number
  overallProgressPct: number
  baseCurrencyCode: string
  goals: Goal[]
}

/** One contribution (signed amount in the goal's currency). */
export interface GoalContribution {
  id: number
  goalId: number
  amountMinor: number
  contributedOn: string
  method: GoalContributionMethod
  transactionId: number | null
  notes: string | null
}

export type GoalContributionPage = PageResponse<GoalContribution>

/** One bucket of the cumulative chart series. */
export interface ChartPoint {
  period: string
  addedMinor: number
  cumulativeMinor: number
}

export interface MilestoneStatus {
  pct: number
  amountMinor: number
  crossedAt: string | null
  crossed: boolean
}

export interface GoalProgress {
  goal: Goal
  contributions: GoalContribution[]
  chart: ChartPoint[]
  milestones: MilestoneStatus[]
}

export interface CreateGoalInput {
  name: string
  description?: string | null
  targetAmountMinor: number
  currencyCode: string
  targetDate?: string | null
  linkedAccountId?: number | null
  priority?: number
  icon?: string | null
  color?: string | null
  milestonePct?: number
}

export interface UpdateGoalInput {
  name?: string
  description?: string | null
  targetAmountMinor?: number
  targetDate?: string
  clearTargetDate?: boolean
  linkedAccountId?: number
  clearLinkedAccount?: boolean
  priority?: number
  icon?: string | null
  color?: string | null
  milestonePct?: number
  status?: GoalStatus
  version?: number
}

export interface CreateContributionInput {
  amountMinor: number
  contributedOn?: string
  method?: GoalContributionMethod
  transactionId?: number | null
  notes?: string | null
}

export type ChartGranularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'