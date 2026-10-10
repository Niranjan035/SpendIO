import { formatBusinessDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'

export interface SchedulePreviewProps {
  dates: readonly string[]
  /** Frozen per-occurrence estimate in the base currency (absent for unsaved rules). */
  estimatedAmountMinor?: number | null
  currencyCode?: string
  /** When false the rule will not actually run on those dates (e.g. paused). */
  willRun?: boolean
}

/**
 * The next-N occurrence preview shown in the create/edit form (ARCHITECTURE.md E.9):
 * a compact, scrollable list of dates with the frozen base-currency estimate.
 */
export function SchedulePreview({
  dates,
  estimatedAmountMinor,
  currencyCode = 'USD',
  willRun = true,
}: SchedulePreviewProps) {
  if (dates.length === 0) {
    return <p className="text-xs text-muted">No upcoming occurrences.</p>
  }
  return (
    <div className="rounded-md border border-border bg-surface-sunken p-3">
      <p className="mb-2 text-xs font-medium text-muted">Upcoming occurrences</p>
      <ul className="flex flex-col gap-1.5">
        {dates.map((date) => (
          <li key={date} className="flex items-center justify-between gap-3 text-sm">
            <span className="text-text">{formatBusinessDate(date)}</span>
            <span className="flex items-center gap-2">
              {estimatedAmountMinor !== null && estimatedAmountMinor !== undefined && (
                <span className="num text-subtle">
                  {formatMoney(estimatedAmountMinor, currencyCode)}
                </span>
              )}
              {willRun && <span className="text-xs text-success">will run</span>}
              {!willRun && <span className="text-xs text-warning">skipped while paused</span>}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}