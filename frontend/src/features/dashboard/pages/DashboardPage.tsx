import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { cn } from '@/lib/cn'
import { paths } from '@/routes/paths'
import { TRANSACTION_TYPE_LABELS, transactionTypeTone } from '@/features/transactions/labels'
import {
  useDashboardSummary,
  useDashboardUpcomingRecurring,
  useRecentTransactions,
} from '@/features/analytics/hooks'
import { formatDeltaPct } from '@/features/analytics/labels'

/**
 * Nullable comparison deltas are omitted from the wire by the backend's NON_NULL
 * Jackson policy, so a "no comparison" value may arrive as null or undefined.
 */
function deltaTone(
  pct: number | null | undefined,
  invert = false,
): 'success' | 'danger' | 'neutral' {
  if (pct === null || pct === undefined || pct === 0) return 'neutral'
  const up = pct > 0
  const good = invert ? !up : up
  return good ? 'success' : 'danger'
}

function DeltaBadge({
  pct,
  inverse,
}: {
  pct: number | null | undefined
  inverse?: boolean
}) {
  return <Badge tone={deltaTone(pct, inverse)}>{formatDeltaPct(pct)}</Badge>
}

/** Percentage value for a summary stat subline; null -> "—". */
function pct(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${value.toFixed(1)}%`
}

export function DashboardPage() {
  const navigate = useNavigate()
  const summary = useDashboardSummary()
  const recent = useRecentTransactions(5)
  const upcoming = useDashboardUpcomingRecurring(30)

  const data = summary.data
  const transactions = recent.data ?? []
  const rules = upcoming.data ?? []

  const cards = data
    ? [
        {
          label: 'Net worth',
          value: formatMoney(data.totalBalanceBaseMinor, data.baseCurrencyCode),
          delta: <DeltaBadge pct={data.compare.deltas.savingsPct} />,
          note: 'vs previous period',
        },
        {
          label: 'Income this month',
          value: formatMoney(data.incomeBaseMinor, data.baseCurrencyCode),
          delta: <DeltaBadge pct={data.compare.deltas.incomePct} />,
          note: `${data.transactionCount} transaction${data.transactionCount === 1 ? '' : 's'}`,
        },
        {
          label: 'Spending this month',
          value: formatMoney(data.expenseBaseMinor, data.baseCurrencyCode),
          delta: <DeltaBadge pct={data.compare.deltas.expensePct} inverse />,
          note: 'vs previous period',
        },
        {
          label: 'Savings rate',
          value: pct(data.savingsRatePct),
          delta: <DeltaBadge pct={data.compare.deltas.savingsPct} />,
          note: `${data.savingsBaseMinor > 0 ? formatMoney(data.savingsBaseMinor, data.baseCurrencyCode) : '—'} saved`,
        },
      ]
    : []

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold text-text">Dashboard</h1>
        <p className="mt-1 text-sm text-muted">Your financial snapshot.</p>
      </header>

      <section aria-label="Summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {summary.isLoading &&
          cards.length === 0 &&
          Array.from({ length: 4 }).map((_, index) => (
            <Card key={index}>
              <CardBody>
                <p className="text-xs uppercase tracking-wide text-subtle">Loading…</p>
              </CardBody>
            </Card>
          ))}
        {cards.map((card) => (
          <Card key={card.label}>
            <CardBody>
              <p className="text-xs uppercase tracking-wide text-subtle">{card.label}</p>
              <p className="mt-2 text-2xl font-semibold text-text num">{card.value}</p>
              <p className="mt-2 flex items-center gap-2 text-xs text-muted">
                {card.delta}
                <span>{card.note}</span>
              </p>
            </CardBody>
          </Card>
        ))}
      </section>

      <section aria-label="Ledger and budgets" className="grid gap-4 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader
            title="Recent transactions"
            description="The latest ledger rows"
            actions={
              <Button variant="ghost" size="sm" onClick={() => navigate(paths.transactions)}>
                View all
              </Button>
            }
          />
          <CardBody className="p-0">
            {recent.isLoading && <p className="p-4 text-sm text-muted">Loading…</p>}
            {!recent.isLoading && transactions.length === 0 && (
              <p className="p-4 text-sm text-muted">No transactions yet.</p>
            )}
            {transactions.length > 0 && (
              <ul className="divide-y divide-border">
                {transactions.map((transaction) => (
                  <li key={transaction.id} className="flex items-center gap-3 px-4 py-3">
                    <Badge tone={transactionTypeTone(transaction.type)}>
                      {TRANSACTION_TYPE_LABELS[transaction.type]}
                    </Badge>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-text">
                        {transaction.description ?? transaction.category?.name ?? 'Untitled'}
                      </p>
                      <p className="mt-0.5 text-xs text-muted">
                        {transaction.account.name}
                        <span className="mx-1.5">·</span>
                        <span className="num">{formatBusinessDate(transaction.occurredOn)}</span>
                      </p>
                    </div>
                    <span
                      className={cn(
                        'shrink-0 text-sm font-semibold num',
                        transaction.type === 'INCOME' ? 'text-success' : 'text-text',
                      )}
                    >
                      {transaction.direction === 'INFLOW' ? '+' : '\u2212'}
                      {formatMoney(transaction.amountMinor, transaction.currencyCode)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title="Upcoming recurring"
            description="Due within the next 30 days"
            actions={
              <Button variant="ghost" size="sm" onClick={() => navigate(paths.recurring)}>
                Manage
              </Button>
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
                      <p className="mt-0.5 text-xs text-muted num">
                        {formatBusinessDate(rule.nextRunDate)}
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
      </section>

      <section aria-label="Budget and goals" className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Budget utilisation</p>
            <p className="mt-2 text-2xl font-semibold text-text num">{pct(data?.budgetUtilisationPct)}</p>
            <p className="mt-1 text-xs text-muted">Across active budgets</p>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Goal progress</p>
            <p className="mt-2 text-2xl font-semibold text-text num">{pct(data?.goalProgressPct)}</p>
            <p className="mt-1 text-xs text-muted">Across active goals</p>
          </CardBody>
        </Card>
      </section>
    </div>
  )
}