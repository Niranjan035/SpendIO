import { format } from 'date-fns'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { parseApiDate } from '@/lib/dates'
import { useLogoutEverywhere, useRevokeSession, useSessions } from '../hooks'

function formatTimestamp(value: string | null): string {
  const date = parseApiDate(value)
  return date ? format(date, 'dd MMM yyyy HH:mm') : '—'
}

/** GET /auth/sessions plus revoke and "log out everywhere" (ARCHITECTURE.md D.2, E.2). */
export function SessionsList() {
  const sessions = useSessions()
  const revoke = useRevokeSession()
  const logoutEverywhere = useLogoutEverywhere()
  const toast = useToast()

  const onRevoke = (id: string) =>
    revoke.mutate(id, {
      onSuccess: () => toast.push({ tone: 'success', message: 'Session revoked' }),
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'Could not revoke the session',
        }),
    })

  const onLogoutEverywhere = () =>
    logoutEverywhere.mutate(undefined, {
      onError: () =>
        toast.push({ tone: 'error', message: 'Could not reach the server; signing out locally' }),
    })

  if (sessions.isLoading) return <LoadingSpinner label="Loading sessions" />
  if (sessions.isError) {
    return (
      <p role="alert" className="text-sm text-danger">
        Could not load your active sessions.
      </p>
    )
  }

  const rows = sessions.data ?? []

  return (
    <div className="flex flex-col gap-4">
      {rows.length === 0 ? (
        <p className="text-sm text-muted">No active sessions.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs uppercase tracking-wide text-subtle">
                <th className="py-2 pr-3 font-medium">Device</th>
                <th className="py-2 pr-3 font-medium">IP address</th>
                <th className="py-2 pr-3 font-medium">Last used</th>
                <th className="py-2 pr-3 font-medium">Expires</th>
                <th className="py-2 font-medium" />
              </tr>
            </thead>
            <tbody>
              {rows.map((session) => (
                <tr key={session.id} className="border-b border-border/60">
                  <td className="py-2 pr-3 text-text">
                    <span className="flex items-center gap-2">
                      {session.deviceLabel ?? 'Unknown device'}
                      {session.current && <Badge tone="success">This device</Badge>}
                    </span>
                  </td>
                  <td className="py-2 pr-3 text-muted">{session.ipAddress ?? '—'}</td>
                  <td className="py-2 pr-3 text-muted">{formatTimestamp(session.lastUsedAt)}</td>
                  <td className="py-2 pr-3 text-muted">{formatTimestamp(session.expiresAt)}</td>
                  <td className="py-2 text-right">
                    {!session.current && (
                      <Button
                        size="sm"
                        variant="secondary"
                        loading={revoke.isPending && revoke.variables === session.id}
                        onClick={() => onRevoke(session.id)}
                      >
                        Revoke
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex justify-end">
        <Button
          variant="danger"
          loading={logoutEverywhere.isPending}
          onClick={onLogoutEverywhere}
        >
          Log out everywhere
        </Button>
      </div>
    </div>
  )
}
