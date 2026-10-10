import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { paths } from '@/routes/paths'
import { useMarkAllRead, useNotifications, useUnreadCount } from '../hooks'
import { NotificationItem } from './NotificationItem'

export function NotificationBell() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const unread = useUnreadCount()
  const recent = useNotifications({ unreadOnly: true, size: 5 })
  const markAllRead = useMarkAllRead()

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    window.addEventListener('pointerdown', onPointerDown)
    return () => window.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  const count = unread.data?.total ?? 0

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        className="relative flex h-9 w-9 items-center justify-center rounded-md border border-border text-muted transition-colors hover:bg-surface-sunken hover:text-text"
        onClick={() => setOpen((current) => !current)}
        aria-label={`Notifications${count > 0 ? `, ${count} unread` : ''}`}
        aria-expanded={open}
      >
        <Bell className="h-4 w-4" aria-hidden />
        {count > 0 && (
          <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-white">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 overflow-hidden rounded-lg border border-border-strong bg-surface shadow-lg">
          <header className="flex items-center justify-between gap-2 border-b border-border px-4 py-3">
            <h2 className="text-sm font-semibold text-text">Notifications</h2>
            {count > 0 && (
              <Button
                variant="ghost"
                size="sm"
                loading={markAllRead.isPending}
                onClick={() => void markAllRead.mutateAsync()}
              >
                Mark all read
              </Button>
            )}
          </header>
          <div className="max-h-96 overflow-y-auto">
            {recent.isLoading && (
              <div className="flex justify-center py-8">
                <LoadingSpinner label="Loading notifications" />
              </div>
            )}
            {!recent.isLoading && (recent.data?.content.length ?? 0) === 0 && (
              <p className="px-4 py-8 text-center text-sm text-muted">You are all caught up.</p>
            )}
            {(recent.data?.content ?? []).map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                onOpen={() => navigate(paths.notifications)}
              />
            ))}
          </div>
          <footer className="border-t border-border px-4 py-3">
            <Button
              variant="secondary"
              size="sm"
              className="w-full"
              onClick={() => {
                setOpen(false)
                navigate(paths.notifications)
              }}
            >
              View all
            </Button>
          </footer>
        </div>
      )}
    </div>
  )
}