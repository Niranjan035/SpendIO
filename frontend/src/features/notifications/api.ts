import { http, type QueryValue } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  NotificationFilter,
  NotificationPage,
  NotificationPreferencesResponse,
  Notification,
  UnreadCount,
  UpdateNotificationPreferenceInput,
} from './types'

export function buildNotificationQuery(filter: NotificationFilter): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {}
  if (filter.unreadOnly) query.unreadOnly = true
  if (filter.type) query.type = filter.type
  if (filter.severity) query.severity = filter.severity
  if (filter.page !== undefined) query.page = filter.page
  if (filter.size !== undefined) query.size = filter.size
  return query
}

export const notificationsApi = {
  list(filter: NotificationFilter): Promise<NotificationPage> {
    return http.get<NotificationPage>(endpoints.notifications.list, {
      query: { ...buildNotificationQuery(filter), page: filter.page ?? 0, size: filter.size ?? 20 },
    })
  },

  unreadCount(): Promise<UnreadCount> {
    return http.get<UnreadCount>(endpoints.notifications.unreadCount)
  },

  read(id: number): Promise<Notification> {
    return http.patch<Notification>(endpoints.notifications.read(String(id)))
  },

  readAll(): Promise<UnreadCount> {
    return http.post<UnreadCount>(endpoints.notifications.readAll)
  },

  dismiss(id: number): Promise<void> {
    return http.post<void>(endpoints.notifications.dismiss(String(id)))
  },

  remove(id: number): Promise<void> {
    return http.delete<void>(endpoints.notifications.remove(String(id)))
  },

  preferences(): Promise<NotificationPreferencesResponse> {
    return http.get<NotificationPreferencesResponse>(endpoints.notifications.preferences)
  },

  updatePreference(input: UpdateNotificationPreferenceInput): Promise<NotificationPreferencesResponse> {
    return http.put<NotificationPreferencesResponse>(endpoints.notifications.preferences, input)
  },
}