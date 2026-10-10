import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { useMarkAllRead, useNotifications, useUnreadCount } from '../hooks'
import {
  NOTIFICATION_SEVERITIES,
  NOTIFICATION_SEVERITY_LABELS,
  NOTIFICATION_TYPES,
  NOTIFICATION_TYPE_LABELS,
} from '../labels'
import type { Notification, NotificationFilter, NotificationSeverity, NotificationType } from '../types'
import { NotificationItem } from '../components/NotificationItem'

export function NotificationsPage() {
  const [type, setType] = useState<NotificationType | ''>('')
  const [severity, setSeverity] = useState<NotificationSeverity | ''>('')
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)

  const filter: NotificationFilter = useMemo(
    () => ({ type, severity: severity === '' ? undefined : severity, unreadOnly, page, size: 20 }),
    [type, severity, unreadOnly, page],
  )

  const inbox = useNotifications(filter)
  const unread = useUnreadCount()
  const markAllRead = useMarkAllRead()

  const items = inbox.data?.content ?? []
  const hasUnread = (unread.data?.total ?? 0) > 0

  const onOpen = (notification: Notification) => {
    if (notification.actionUrl) window.open(notification.actionUrl, '_blank', 'noopener,noreferrer')
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Notifications</h1>
          <p className="mt-1 text-sm text-muted">Alerts about budgets, goals, recurring bills and imports.</p>
        </div>
        {hasUnread && (
          <Button variant="secondary" loading={markAllRead.isPending} onClick={() => void markAllRead.mutateAsync()}>
            Mark all read
          </Button>
        )}
      </header>

      <section aria-label="Filters" className="flex flex-wrap items-end gap-4">
        <div className="w-48">
          <Select label="Type" value={type} onChange={(event) => setType(event.target.value as NotificationType | '')}>
            <option value="">All types</option>
            {NOTIFICATION_TYPES.map((option) => (
              <option key={option} value={option}>
                {NOTIFICATION_TYPE_LABELS[option]}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-40">
          <Select
            label="Severity"
            value={severity}
            onChange={(event) => setSeverity(event.target.value as NotificationSeverity | '')}
          >
            <option value="">All severities</option>
            {NOTIFICATION_SEVERITIES.map((option) => (
              <option key={option} value={option}>
                {NOTIFICATION_SEVERITY_LABELS[option]}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-40">
          <Select
            label="Show"
            value={unreadOnly ? 'unread' : 'all'}
            onChange={(event) => {
              setUnreadOnly(event.target.value === 'unread')
              setPage(0)
            }}
          >
            <option value="all">All</option>
            <option value="unread">Unread only</option>
          </Select>
        </div>
      </section>

      <section aria-label="Inbox">
        <Card>
          <CardHeader title="Inbox" />
          <CardBody className="p-0">
            {inbox.isLoading && (
              <div className="flex justify-center py-10">
                <LoadingSpinner label="Loading notifications" />
              </div>
            )}
            {inbox.isError && (
              <p role="alert" className="px-4 py-8 text-sm text-danger">
                Could not load notifications.
              </p>
            )}
            {!inbox.isLoading && !inbox.isError && items.length === 0 && (
              <div className="p-10 text-center">
                <p className="text-sm text-muted">No notifications match these filters.</p>
              </div>
            )}
            {items.length > 0 && (
              <div className="divide-y divide-border">
                {items.map((notification) => (
                  <NotificationItem key={notification.id} notification={notification} onOpen={onOpen} />
                ))}
              </div>
            )}
          </CardBody>
        </Card>

        {inbox.data && inbox.data.totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <Button variant="secondary" disabled={inbox.data.first} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </Button>
            <span className="text-sm text-muted">
              Page {inbox.data.page + 1} of {inbox.data.totalPages}
            </span>
            <Button
              variant="secondary"
              disabled={inbox.data.last}
              onClick={() => setPage((p) => Math.min(inbox.data!.totalPages - 1, p + 1))}
            >
              Next
            </Button>
          </div>
        )}
      </section>
    </div>
  )
}