import { Badge } from '@/components/ui/Badge'
import { cn } from '@/lib/cn'
import {
  notificationSeverityTone,
  notificationTime,
  notificationTypeTone,
  NOTIFICATION_SEVERITY_LABELS,
  NOTIFICATION_TYPE_LABELS,
} from '../labels'
import type { Notification } from '../types'

interface NotificationItemProps {
  notification: Notification
  onOpen?: (notification: Notification) => void
  onMarkRead?: (notification: Notification) => void
  onDismiss?: (notification: Notification) => void
  onDelete?: (notification: Notification) => void
}

export function NotificationItem({
  notification,
  onOpen,
  onMarkRead,
  onDismiss,
  onDelete,
}: NotificationItemProps) {
  const unread = notification.readAt === null
  const title = (
    <button
      type="button"
      className="text-left transition-colors hover:text-primary"
      onClick={(event) => {
        event.stopPropagation()
        onOpen?.(notification)
      }}
    >
      {notification.title}
    </button>
  )

  return (
    <article
      className={cn(
        'flex flex-col gap-2 border-b border-border p-4 transition-colors last:border-b-0',
        unread && 'bg-primary-subtle/40',
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        {unread && <span className="h-2 w-2 rounded-full bg-primary" aria-label="Unread" />}
        <Badge tone={notificationTypeTone(notification.type)}>
          {NOTIFICATION_TYPE_LABELS[notification.type]}
        </Badge>
        <Badge tone={notificationSeverityTone(notification.severity)}>
          {NOTIFICATION_SEVERITY_LABELS[notification.severity]}
        </Badge>
        <span className="ml-auto text-xs text-subtle">{notificationTime(notification.createdAt)}</span>
      </div>
      <div className={cn('text-sm text-text', unread ? 'font-medium' : 'font-normal')}>{title}</div>
      <p className="text-sm text-muted">{notification.body}</p>
      <div className="flex flex-wrap gap-2">
        {unread && onMarkRead && (
          <button
            type="button"
            className="text-xs font-medium text-primary transition-colors hover:text-primary-hover"
            onClick={() => onMarkRead(notification)}
          >
            Mark as read
          </button>
        )}
        {onDismiss && (
          <button
            type="button"
            className="text-xs text-subtle transition-colors hover:text-text"
            onClick={() => onDismiss(notification)}
          >
            Dismiss
          </button>
        )}
        {onDelete && (
          <button
            type="button"
            className="text-xs text-subtle transition-colors hover:text-danger"
            onClick={() => onDelete(notification)}
          >
            Delete
          </button>
        )}
      </div>
    </article>
  )
}