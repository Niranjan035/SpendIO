import { useState } from 'react'
import { format } from 'date-fns'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { formatBusinessDate, parseApiDate } from '@/lib/dates'
import { RECURRING_RUN_STATUS_LABELS, recurringRunStatusTone } from '../labels'
import { useRecurringRuns } from '../hooks'

export interface RunHistoryTableProps {
  ruleId: number
}

function formatTimestamp(value: string): string {
  const date = parseApiDate(value)
  return date ? format(date, 'dd MMM yyyy HH:mm') : '—'
}

/**
 * Execution history of one rule (ARCHITECTURE.md E.9 {@code GET /{id}/runs}): one row
 * per scheduled occurrence with its outcome and the posted ledger transaction.
 */
export function RunHistoryTable({ ruleId }: RunHistoryTableProps) {
  const [page, setPage] = useState(0)
  const runs = useRecurringRuns(ruleId, page)

  if (runs.isLoading) {
    return <p className="py-4 text-sm text-muted">Loading run history…</p>
  }
  const content = runs.data?.content ?? []

  return (
    <div className="flex flex-col gap-3">
      {content.length === 0 ? (
        <p className="py-4 text-sm text-muted">No runs recorded yet.</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-border text-xs uppercase tracking-wide text-subtle">
              <th className="py-2 pr-2 font-medium">Scheduled</th>
              <th className="py-2 pr-2 font-medium">Ran at</th>
              <th className="py-2 pr-2 font-medium">Status</th>
              <th className="py-2 pr-2 text-right font-medium">Attempts</th>
              <th className="py-2 font-medium">Details</th>
            </tr>
          </thead>
          <tbody>
            {content.map((run) => (
              <tr key={run.id} className="border-b border-border/60">
                <td className="py-2 pr-2 text-text">{formatBusinessDate(run.scheduledFor)}</td>
                <td className="py-2 pr-2 text-muted">{formatTimestamp(run.executedAt)}</td>
                <td className="py-2 pr-2">
                  <Badge tone={recurringRunStatusTone(run.status)}>
                    {RECURRING_RUN_STATUS_LABELS[run.status]}
                  </Badge>
                </td>
                <td className="py-2 pr-2 text-right num text-muted">{run.attempts}</td>
                <td className="py-2 text-muted">
                  {run.errorMessage ?? (run.transactionId ? `Transaction #${run.transactionId}` : '—')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {runs.data && (runs.data.content.length > 0 || runs.data.page > 0) && (
        <div className="flex items-center justify-end gap-2">
          <Button
            size="sm"
            variant="secondary"
            disabled={runs.data.first}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            Newer
          </Button>
          <span className="text-xs text-muted">
            Page {runs.data.page + 1} of {Math.max(1, runs.data.totalPages)}
          </span>
          <Button
            size="sm"
            variant="secondary"
            disabled={runs.data.last}
            onClick={() => setPage((current) => current + 1)}
          >
            Older
          </Button>
        </div>
      )}
    </div>
  )
}