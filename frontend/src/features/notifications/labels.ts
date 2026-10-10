import { format } from 'date-fns'
import { parseApiDate } from '@/lib/dates'
import type { BadgeTone } from '@/components/ui/Badge'
import type { NotificationSeverity, NotificationType } from './types'

export const NOTIFICATION_TYPES: readonly NotificationType[] = [
  'BUDGET_ALERT',
  'GOAL_MILESTONE',
  'GOAL_REMINDER',
  'RECURRING_DUE',
  'RECURRING_FAILED',
  'IMPORT_COMPLETED',
  'IMPORT_FAILED',
  'SYSTEM',
]

export const NOTIFICATION_SEVERITIES: readonly NotificationSeverity[] = [
  'INFO',
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
]

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  BUDGET_ALERT: 'Budget alert',
  GOAL_MILESTONE: 'Goal milestone',
  GOAL_REMINDER: 'Goal reminder',
  RECURRING_DUE: 'Recurring due',
  RECURRING_FAILED: 'Recurring failed',
  IMPORT_COMPLETED: 'Import completed',
  IMPORT_FAILED: 'Import failed',
  SYSTEM: 'System',
}

export const NOTIFICATION_SEVERITY_LABELS: Record<NotificationSeverity, string> = {
  INFO: 'Info',
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
}

export function notificationTypeTone(type: NotificationType): BadgeTone {
  switch (type) {
    case 'BUDGET_ALERT':
      return 'warning'
    case 'GOAL_MILESTONE':
    case 'IMPORT_COMPLETED':
      return 'success'
    case 'GOAL_REMINDER':
    case 'RECURRING_DUE':
      return 'info'
    case 'RECURRING_FAILED':
    case 'IMPORT_FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

export function notificationSeverityTone(severity: NotificationSeverity): BadgeTone {
  switch (severity) {
    case 'CRITICAL':
    case 'HIGH':
      return 'danger'
    case 'MEDIUM':
      return 'warning'
    case 'LOW':
      return 'info'
    default:
      return 'neutral'
  }
}

export function notificationTime(value: string): string {
  const date = parseApiDate(value)
  return date ? format(date, 'dd MMM yyyy, HH:mm') : '—'
}