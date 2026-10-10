import { Link } from 'react-router-dom'
import { paths } from '@/routes/paths'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { TRANSACTION_TYPE_LABELS, transactionTypeTone } from '@/features/transactions/labels'
import { useUpcomingRecurrings } from '../hooks'

export interface UpcomingRecurringCardProps {
  days?: number
}

/**
 * Dashboard snapshot of what the recurring scheduler will do next (ARCHITECTURE.md E.9
 * {@code GET /recurring-transactions/upcoming}): up to five rules due in the window.
 */
export function UpcomingRecurringCard({ days = 30 }: UpcomingRecurringCardProps) {
  const upcoming = useUpcomingRecurrings(days)
  const rules = upcoming.data ?? []

  return (
    <Card>
      <CardHeader
        title="Upcoming recurring"
        description={`Due within the next ${days} days`}
        actions={
          <Link
            to={paths.recurring}
            className="text-sm font-medium text-primary hover:underline"
          >
            Manage
          </Link>
        }
      />
      <CardBody>
        {upcoming.isLoading && <p className="text-sm text-muted">Loading…</p>}
        {!upcoming.isLoading && rules.length === 0 && (
          <p className="text-sm text-muted">Nothing scheduled in this window.</p>
        )}
        {rules.length > 0 && (
          <ul className="flex flex-col gap-3">
            {rules.slice(0, 5).map((rule) => (
              <li key={rule.id} className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-text">
                    {rule.description ?? TRANSACTION_TYPE_LABELS[rule.type]}
                  </p>
                  <p className="mt-0.5 flex items-center gap-2 text-xs text-muted">
                    <span className="truncate">{rule.account.name}</span>
                    <Badge tone={transactionTypeTone(rule.type)}>
                      {TRANSACTION_TYPE_LABELS[rule.type]}
                    </Badge>
                    <span className="num">{formatBusinessDate(rule.nextRunDate)}</span>
                  </p>
                </div>
                <span className="shrink-0 text-sm font-semibold text-text num">
                  {formatMoney(rule.amountMinor, rule.currencyCode)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  )
}