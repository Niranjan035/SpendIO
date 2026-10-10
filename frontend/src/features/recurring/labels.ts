import type { BadgeTone } from '@/components/ui/Badge'
import type { RecurrenceFrequency, RecurringRunStatus, RecurringStatus } from '@/types/enums'

export const RECURRENCE_FREQUENCY_LABELS: Record<RecurrenceFrequency, string> = {
  DAILY: 'Daily',
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
  YEARLY: 'Yearly',
  CUSTOM: 'Custom',
}

export const RECURRING_STATUS_LABELS: Record<RecurringStatus, string> = {
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
}

export const RECURRING_RUN_STATUS_LABELS: Record<RecurringRunStatus, string> = {
  SUCCESS: 'Success',
  SKIPPED: 'Skipped',
  FAILED: 'Failed',
  SKIPPED_DUPLICATE: 'Duplicate',
  SKIPPED_PAUSED: 'Paused',
}

export function recurringStatusTone(status: RecurringStatus): BadgeTone {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'PAUSED':
      return 'warning'
    case 'COMPLETED':
      return 'neutral'
    case 'FAILED':
      return 'danger'
    case 'CANCELLED':
      return 'neutral'
  }
}

export function recurringRunStatusTone(status: RecurringRunStatus): BadgeTone {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'SKIPPED':
      return 'warning'
    case 'SKIPPED_DUPLICATE':
      return 'info'
    case 'SKIPPED_PAUSED':
      return 'neutral'
  }
}

/** Summarises how often a rule fires into a single short label, e.g. "Every 2 months". */
export function recurringEveryLabel(
  frequency: RecurrenceFrequency,
  intervalCount: number,
): string {
  const noun = RECURRENCE_FREQUENCY_LABELS[frequency].toLowerCase()
  const count = Math.max(1, intervalCount)
  if (count === 1) return `Every ${noun}`
  return `Every ${count} ${noun}`
}