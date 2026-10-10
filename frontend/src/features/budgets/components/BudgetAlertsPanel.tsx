import { useState } from 'react'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney, fractionDigitsFor } from '@/lib/money'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { useAcknowledgeBudgetAlert, useBudgetAlerts, useEvaluateBudgets } from '../hooks'

export interface BudgetAlertsPanelProps {
  /** Only show alerts for this budget when set; otherwise the whole inbox. */
  budgetId?: number
}

/** The alert inbox: threshold crossings with one-tap acknowledgement (E.10). */
export function BudgetAlertsPanel({ budgetId }: BudgetAlertsPanelProps) {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const alerts = useBudgetAlerts({
    unacknowledgedOnly: budgetId === undefined ? undefined : true,
    page,
    size: 20,
  })
  const acknowledge = useAcknowledgeBudgetAlert()
  const evaluate = useEvaluateBudgets()

  const notifyError = (cause: unknown, fallback: string): void => {
    toast.push({ tone: 'error', message: cause instanceof ApiError ? cause.message : fallback })
  }

  const rows = (alerts.data?.content ?? []).filter(
    (alert) => budgetId === undefined || alert.budgetId === budgetId,
  )

  return (
    <Card>
      <CardHeader
        title="Alerts"
        actions={
          <Button
            variant="secondary"
            size="sm"
            loading={evaluate.isPending}
            onClick={() => evaluate.mutate(undefined, { onError: (cause) => notifyError(cause, 'Could not run the check') })}
          >
            Re-check thresholds
          </Button>
        }
      />
      <CardBody>
        {alerts.isLoading && <p className="text-sm text-muted">Loading alerts…</p>}
        {alerts.isError && (
          <p role="alert" className="text-sm text-danger">
            {alerts.error instanceof ApiError ? alerts.error.message : 'Could not load alerts.'}
          </p>
        )}
        {alerts.data && rows.length === 0 && (
          <p className="text-sm text-muted">No alerts in this period.</p>
        )}
        {rows.length > 0 && (
          <ul className="divide-y divide-border text-sm">
            {rows.map((alert) => {
              const digits = fractionDigitsFor(alert.currencyCode)
              return (
                <li key={alert.id} className="flex items-center justify-between gap-3 py-2.5">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium text-text">{alert.budgetName}</span>
                      <Badge tone={alert.thresholdPct >= 100 ? 'danger' : 'warning'}>
                        {alert.thresholdPct}% used
                      </Badge>
                      {alert.acknowledged && <Badge tone="neutral">Seen</Badge>}
                    </div>
                    <p className="mt-0.5 text-xs text-muted">
                      {formatBusinessDate(alert.periodStart)} – {formatBusinessDate(alert.periodEnd)}
                      {' · '}
                      {formatMoney(alert.spentAmountMinor, alert.currencyCode, digits)} of{' '}
                      {formatMoney(alert.budgetAmountMinor, alert.currencyCode, digits)}
                    </p>
                  </div>
                  {!alert.acknowledged && (
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={acknowledge.isPending}
                      onClick={() =>
                        acknowledge.mutate(
                          { budgetId: alert.budgetId, alertId: alert.id },
                          {
                            onSuccess: () => toast.push({ tone: 'success', message: 'Alert acknowledged' }),
                            onError: (cause) => notifyError(cause, 'Could not acknowledge the alert'),
                          },
                        )
                      }
                    >
                      Acknowledge
                    </Button>
                  )}
                </li>
              )
            })}
          </ul>
        )}
        {alerts.data && alerts.data.totalPages > 1 && (
          <div className="mt-3 flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              disabled={alerts.data.first}
              onClick={() => setPage((current) => current - 1)}
            >
              Previous
            </Button>
            <span className="text-xs text-muted">
              Page {alerts.data.page + 1} of {alerts.data.totalPages}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={alerts.data.last}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
            </Button>
          </div>
        )}
      </CardBody>
    </Card>
  )
}