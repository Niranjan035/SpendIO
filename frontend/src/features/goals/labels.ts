import type { BadgeTone } from '@/components/ui/Badge'
import type { GoalContributionMethod, GoalStatus } from '@/types/enums'

export const GOAL_STATUS_LABELS: Record<GoalStatus, string> = {
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
  PAUSED: 'Paused',
  CANCELLED: 'Cancelled',
}

export const GOAL_CONTRIBUTION_METHOD_LABELS: Record<GoalContributionMethod, string> = {
  MANUAL: 'Manual',
  TRANSFER: 'Transfer',
  ADJUSTMENT: 'Adjustment',
}

export const GOAL_PRIORITY_LABELS: Record<number, string> = {
  0: 'Low',
  1: 'Low+',
  2: 'Medium',
  3: 'Medium+',
  4: 'High',
  5: 'Critical',
}

export function goalStatusTone(status: GoalStatus): BadgeTone {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'COMPLETED':
      return 'info'
    case 'PAUSED':
      return 'warning'
    case 'CANCELLED':
      return 'neutral'
  }
}

export function goalMethodTone(method: GoalContributionMethod): BadgeTone {
  switch (method) {
    case 'MANUAL':
      return 'neutral'
    case 'TRANSFER':
      return 'info'
    case 'ADJUSTMENT':
      return 'warning'
  }
}

/** Clamped 0–100 percentage for progress bars. */
export function progressPercent(progressPct: number): number {
  if (!Number.isFinite(progressPct)) return 0
  return Math.max(0, Math.min(100, Math.round(progressPct)))
}