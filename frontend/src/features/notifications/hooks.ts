import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { notificationsApi } from './api'
import type {
  NotificationFilter,
  NotificationSeverity,
  NotificationType,
  UnreadCount,
  UpdateNotificationPreferenceInput,
} from './types'

export const notificationKeys = {
  all: ['notifications'] as const,
  listAll: ['notifications', 'list'] as const,
  list: (filter: NotificationFilter) => ['notifications', 'list', filter] as const,
  unread: ['notifications', 'unread'] as const,
  preferences: ['notifications', 'preferences'] as const,
}

function invalidateNotifications(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: notificationKeys.all })
}

export function useNotifications(filter: NotificationFilter) {
  return useQuery({
    queryKey: notificationKeys.list(filter),
    queryFn: () => notificationsApi.list(filter),
  })
}

export function useUnreadCount() {
  return useQuery<UnreadCount>({
    queryKey: notificationKeys.unread,
    queryFn: notificationsApi.unreadCount,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  })
}

export function useMarkRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => notificationsApi.read(id),
    onSuccess: (_data) => {
      void client.setQueryData<UnreadCount>(notificationKeys.unread, (current) =>
        current ? { ...current, total: Math.max(0, current.total - 1) } : current,
      )
      void client.invalidateQueries({ queryKey: notificationKeys.listAll })
    },
  })
}

export function useMarkAllRead() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => notificationsApi.readAll(),
    onSuccess: () => {
      void client.setQueryData<UnreadCount>(notificationKeys.unread, { total: 0, byType: {} })
      invalidateNotifications(client)
    },
  })
}

export function useDismissNotification() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => notificationsApi.dismiss(id),
    onSuccess: (_data) => {
      void client.setQueryData<UnreadCount>(notificationKeys.unread, (current) =>
        current ? { ...current, total: Math.max(0, current.total - 1) } : current,
      )
      invalidateNotifications(client)
    },
  })
}

export function useDeleteNotification() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => notificationsApi.remove(id),
    onSuccess: () => invalidateNotifications(client),
  })
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: notificationKeys.preferences,
    queryFn: notificationsApi.preferences,
  })
}

export function useUpdateNotificationPreference() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: UpdateNotificationPreferenceInput) => notificationsApi.updatePreference(input),
    onSuccess: (response) => {
      void client.setQueryData(notificationKeys.preferences, response)
    },
  })
}

export function preferenceForType(
  preferences: ReadonlyArray<{
    type: NotificationType
    channel: 'IN_APP' | 'EMAIL'
    enabled: boolean
    minSeverity: NotificationSeverity | null
  }>,
  type: NotificationType,
  channel: 'IN_APP' | 'EMAIL',
):
  | {
      type: NotificationType
      channel: 'IN_APP' | 'EMAIL'
      enabled: boolean
      minSeverity: NotificationSeverity | null
    }
  | undefined {
  return preferences.find((item) => item.type === type && item.channel === channel)
}