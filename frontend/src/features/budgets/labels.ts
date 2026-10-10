import type { BadgeTone } from '@/components/ui/Badge'
import type { BudgetPeriod, BudgetStatus, PaceStatus } from '@/types/enums'

export const BUDGET_PERIOD_LABELS: Record<BudgetPeriod, string> = {
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  YEARLY: 'Yearly',
  CUSTOM: 'Custom',
}

export const BUDGET_STATUS_LABELS: Record<BudgetStatus, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
}

export const PACE_LABELS: Record<PaceStatus, string> = {
  UNDER_PACE: 'Under pace',
  ON_PACE: 'On pace',
  OVER_PACE: 'Over pace',
  EXCEEDED: 'Above allowance',
}

export function budgetStatusTone(status: BudgetStatus): BadgeTone {
  return status === 'ACTIVE' ? 'success' : 'neutral'
}

export function paceStatusTone(status: PaceStatus): BadgeTone {
  switch (status) {
    case 'UNDER_PACE':
      return 'success'
    case 'ON_PACE':
      return 'info'
    case 'OVER_PACE':
      return 'warning'
    case 'EXCEEDED':
      return 'danger'
  }
}

/** Clamped 0–100 percentage for progress bars. */
export function utilisationPercent(utilisationPct: number): number {
  if (!Number.isFinite(utilisationPct)) return 0
  return Math.max(0, Math.min(100, Math.round(utilisationPct * 100)))
}