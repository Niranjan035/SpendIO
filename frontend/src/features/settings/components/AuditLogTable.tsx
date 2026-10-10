import { useState } from 'react'
import { format } from 'date-fns'
import { Button } from '@/components/ui/Button'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { parseApiDate } from '@/lib/dates'
import { useAuditLogs } from '../hooks'
import type { AuditActorType } from '../types'

const ACTION_LABELS: Record<string, string> = {
  ACCOUNT_DELETED: 'Account deleted',
  PASSWORD_CHANGED: 'Password changed',
  BASE_CURRENCY_CHANGED: 'Base currency changed',
  SESSION_REVOKED: 'Session revoked',
  PROFILE_UPDATED: 'Profile updated',
}

const ACTOR_LABELS: Record<AuditActorType, string> = {
  USER: 'You',
  SYSTEM: 'System',
  JOB: 'Scheduled job',
}

const PAGE_SIZE = 20

function formatTimestamp(value: string): string {
  const date = parseApiDate(value)
  return date ? format(date, 'dd MMM yyyy HH:mm') : '—'
}

/** GET /audit-logs (ARCHITECTURE.md E.3, Phase 14): the caller's own audit trail. */
export function AuditLogTable() {
  const [page, setPage] = useState(0)
  const logs = useAuditLogs(page, PAGE_SIZE)

  if (logs.isLoading) return <LoadingSpinner label="Loading audit trail" />
  if (logs.isError) {
    return (
      <p role="alert" className="text-sm text-danger">
        Could not load your audit trail.
      </p>
    )
  }

  const content = logs.data?.content ?? []

  return (
    <div className="flex flex-col gap-3">
      {content.length === 0 ? (
        <p className="py-4 text-sm text-muted">No audited activity yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs uppercase tracking-wide text-subtle">
                <th className="py-2 pr-3 font-medium">When</th>
                <th className="py-2 pr-3 font-medium">Action</th>
                <th className="py-2 pr-3 font-medium">Actor</th>
                <th className="py-2 pr-3 font-medium">Entity</th>
                <th className="py-2 font-medium">IP address</th>
              </tr>
            </thead>
            <tbody>
              {content.map((entry) => (
                <tr key={entry.id} className="border-b border-border/60">
                  <td className="py-2 pr-3 text-muted">{formatTimestamp(entry.createdAt)}</td>
                  <td className="py-2 pr-3 text-text">
                    {ACTION_LABELS[entry.action] ?? entry.action}
                  </td>
                  <td className="py-2 pr-3 text-muted">{ACTOR_LABELS[entry.actorType]}</td>
                  <td className="py-2 pr-3 text-muted">
                    {entry.entityType
                      ? `${entry.entityType}${entry.entityId ? ` #${entry.entityId}` : ''}`
                      : '—'}
                  </td>
                  <td className="py-2 text-muted">{entry.ipAddress ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {logs.data && (
        <div className="flex items-center justify-end gap-2">
          <Button
            size="sm"
            variant="secondary"
            disabled={logs.data.first}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            Newer
          </Button>
          <span className="text-xs text-muted">
            Page {logs.data.page + 1} of {Math.max(1, logs.data.totalPages)}
          </span>
          <Button
            size="sm"
            variant="secondary"
            disabled={logs.data.last}
            onClick={() => setPage((current) => current + 1)}
          >
            Older
          </Button>
        </div>
      )}
    </div>
  )
}
