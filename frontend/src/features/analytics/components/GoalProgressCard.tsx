import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import { useGoalProgress } from '../hooks'

export function GoalProgressCard() {
  const { data, isLoading, isError } = useGoalProgress()
  const items = data ?? []
  const isEmpty = !isLoading && !isError && items.length === 0

  return (
    <Card>
      <CardHeader title="Goal progress" description="Snapshot of active savings goals" />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No active goals.</p>}
        {!isEmpty && (
          <ul className="space-y-4">
            {items.map((item) => {
              const pct = Math.min(100, item.progressPct)
              return (
                <li key={item.goalId}>
                  <div className="mb-1.5 flex items-center justify-between gap-3">
                    <div className="flex min-w-0 items-center gap-2">
                      <span
                        aria-hidden
                        className={cn(
                          'h-2 w-2 shrink-0 rounded-full',
                          item.onTrack ? 'bg-success' : 'bg-warning',
                        )}
                      />
                      <p className="truncate text-sm font-medium text-text">{item.name}</p>
                    </div>
                    <span className="shrink-0 text-sm font-semibold text-text num">
                      {item.progressPct}%
                    </span>
                  </div>
                  <div
                    role="progressbar"
                    aria-valuenow={item.progressPct}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-label={`${item.name} progress`}
                    className="h-2 w-full overflow-hidden rounded-full bg-surface-sunken"
                  >
                    <div
                      className={cn('h-full rounded-full', item.onTrack ? 'bg-success' : 'bg-warning')}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <p className="mt-1 text-xs text-muted num">
                    {formatMoney(item.currentMinor, item.currencyCode)} of{' '}
                    {formatMoney(item.targetMinor, item.currencyCode)}
                  </p>
                </li>
              )
            })}
          </ul>
        )}
      </CardBody>
    </Card>
  )
}