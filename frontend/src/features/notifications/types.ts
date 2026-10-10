export type NotificationType =
  | 'BUDGET_ALERT'
  | 'GOAL_MILESTONE'
  | 'GOAL_REMINDER'
  | 'RECURRING_DUE'
  | 'RECURRING_FAILED'
  | 'IMPORT_COMPLETED'
  | 'IMPORT_FAILED'
  | 'SYSTEM'

export type NotificationSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type NotificationChannel = 'IN_APP' | 'EMAIL'

export interface Notification {
  id: number
  type: NotificationType
  severity: NotificationSeverity
  title: string
  body: string
  entityType: string | null
  entityId: number | null
  actionUrl: string | null
  payload: Record<string, unknown> | null
  readAt: string | null
  createdAt: string
}

export interface NotificationFilter {
  unreadOnly?: boolean
  type?: NotificationType | ''
  severity?: NotificationSeverity | ''
  page?: number
  size?: number
}

export interface NotificationPage {
  content: Notification[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface UnreadCount {
  total: number
  byType: Partial<Record<NotificationType, number>>
}

export interface NotificationPreference {
  type: NotificationType
  channel: NotificationChannel
  enabled: boolean
  minSeverity: NotificationSeverity | null
}

export interface NotificationPreferencesResponse {
  preferences: NotificationPreference[]
}

export interface UpdateNotificationPreferenceInput {
  type: NotificationType
  channel: NotificationChannel
  enabled: boolean
  minSeverity: NotificationSeverity | null
}