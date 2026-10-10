import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { formatMoney } from '@/lib/money'
import { BUDGET_UTILIZATION_LABELS } from '../labels'
import { useBudgetUtilization } from '../hooks'

function tone(status: 'ON_TRACK' | 'AT_RISK' | 'OVER_BUDGET'): 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'ON_TRACK':
      return 'success'
    case 'AT_RISK':
      return 'warning'
    case 'OVER_BUDGET':
      return 'danger'
  }
}

export function BudgetUtilizationCard() {
  const { data, isLoading, isError } = useBudgetUtilization()
  const items = data ?? []
  const isEmpty = !isLoading && !isError && items.length === 0

  return (
    <Card>
      <CardHeader
        title="Budget utilisation"
        description="How active budgets are pacing against their allowance"
      />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No active budgets.</p>}
        {!isEmpty && (
          <ul className="space-y-4">
            {items.map((item) => {
              const pct = Math.min(100, item.utilisationPct)
              return (
                <li key={item.budgetId}>
                  <div className="mb-1.5 flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-text">{item.name}</p>
                      <p className="mt-0.5 text-xs text-muted num">
                        {formatMoney(item.spentMinor, item.currencyCode)} of{' '}
                        {formatMoney(item.amountMinor, item.currencyCode)}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <span className="text-sm font-semibold text-text num">
                        {item.utilisationPct.toFixed(0)}%
                      </span>
                      <Badge tone={tone(item.status)}>{BUDGET_UTILIZATION_LABELS[item.status]}</Badge>
                    </div>
                  </div>
                  <div
                    role="progressbar"
                    aria-valuenow={Math.round(pct)}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-label={`${item.name} utilisation`}
                    className="h-2 w-full overflow-hidden rounded-full bg-surface-sunken"
                  >
                    <div
                      className="h-full rounded-full bg-primary"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </CardBody>
    </Card>
  )
}