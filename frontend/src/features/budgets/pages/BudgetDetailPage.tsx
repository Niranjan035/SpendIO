import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { Modal } from '@/components/ui/Modal'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney, fractionDigitsFor } from '@/lib/money'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { BUDGET_PERIOD_LABELS, BUDGET_STATUS_LABELS, budgetStatusTone, PACE_LABELS, paceStatusTone, utilisationPercent } from '../labels'
import {
  useBudget,
  useBudgetAlerts,
  useBudgetHistory,
  useBudgetProgress,
  useDeleteBudget,
  useUpdateBudget,
} from '../hooks'
import { BudgetFormModal } from '../components/BudgetFormModal'
import { BudgetProgressBar } from '../components/BudgetProgressBar'
import { BudgetAlertsPanel } from '../components/BudgetAlertsPanel'

export function BudgetDetailPage() {
  const { id: rawId } = useParams<{ id: string }>()
  const id = Number(rawId)
  const navigate = useNavigate()
  const toast = useToast()

  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [periodStart, setPeriodStart] = useState<string | undefined>(undefined)
  const [periodEnd, setPeriodEnd] = useState<string | undefined>(undefined)

  const budget = useBudget(id)
  const progress = useBudgetProgress(id, periodStart, periodEnd)
  const history = useBudgetHistory(id)
  const alerts = useBudgetAlerts({ unacknowledgedOnly: undefined, page: 0, size: 50 })
  const archive = useUpdateBudget()
  const removeBudget = useDeleteBudget()

  if (budget.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted">
        <LoadingSpinner label="Loading budget" /> Loading budget…
      </p>
    )
  }

  if (budget.isError || !budget.data) {
    return (
      <InlineError
        message={budget.error instanceof ApiError ? budget.error.message : 'Could not load the budget.'}
        onRetry={() => void budget.refetch()}
      />
    )
  }

  const current = budget.data
  const digits = fractionDigitsFor(current.currencyCode)
  const slices = history.data?.periods ?? []
  const alertRows = (alerts.data?.content ?? []).filter((alert) => alert.budgetId === id)

  const toggleArchive = (): void => {
    const next = current.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE'
    archive.mutate(
      { id, input: { status: next, version: current.version } },
      {
        onSuccess: () =>
          toast.push({ tone: 'success', message: next === 'ARCHIVED' ? 'Budget archived' : 'Budget reopened' }),
        onError: (cause) =>
          toast.push({ tone: 'error', message: cause instanceof ApiError ? cause.message : 'That did not work' }),
      },
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold text-text">{current.name}</h1>
            <Badge tone={budgetStatusTone(current.status)}>
              {BUDGET_STATUS_LABELS[current.status]}
            </Badge>
          </div>
          <p className="mt-1 text-sm text-muted">
            {BUDGET_PERIOD_LABELS[current.periodType]}
            {current.category ? ` · ${current.category.name}` : ' · Overall'}
            {current.category?.color ? (
              <span
                aria-hidden="true"
                className="ml-2 inline-block h-2.5 w-2.5 rounded-full align-middle"
                style={{ backgroundColor: current.category.color }}
              />
            ) : null}
          </p>
          <button
            type="button"
            onClick={() => navigate('/budgets')}
            className="mt-2 text-sm text-primary hover:underline"
          >
            ← All budgets
          </button>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            Edit
          </Button>
          <Button
            variant="secondary"
            size="sm"
            loading={archive.isPending}
            onClick={() => void toggleArchive()}
          >
            {current.status === 'ACTIVE' ? 'Archive' : 'Reopen'}
          </Button>
          <Button variant="danger" size="sm" onClick={() => setConfirmingDelete(true)}>
            Delete
          </Button>
        </div>
      </header>

      <section aria-label="Progress" className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Progress"
            actions={
              <div className="w-44">
                <Select
                  label="Period"
                  value={periodStart ?? ''}
                  onChange={(event) => {
                    const match = slices.find((slice) => slice.periodStart === event.target.value)
                    setPeriodStart(event.target.value === '' ? undefined : event.target.value)
                    setPeriodEnd(match ? match.periodEnd : undefined)
                  }}
                >
                  <option value="">Current period</option>
                  {slices.map((slice) => (
                    <option key={slice.periodStart} value={slice.periodStart}>
                      {formatBusinessDate(slice.periodStart)} – {formatBusinessDate(slice.periodEnd)}
                    </option>
                  ))}
                </Select>
              </div>
            }
          />
          <CardBody>
            {progress.isLoading && <p className="text-sm text-muted">Loading progress…</p>}
            {progress.isError && (
              <p role="alert" className="text-sm text-danger">
                {progress.error instanceof ApiError ? progress.error.message : 'Could not load progress.'}
              </p>
            )}
            {progress.data && (
              <div className="flex flex-col gap-4">
                <div className="grid gap-4 sm:grid-cols-3">
                  <div>
                    <p className="text-xs uppercase tracking-wide text-subtle">Allowance</p>
                    <p className="mt-1 text-xl font-semibold text-text num">
                      {formatMoney(progress.data.allowanceMinor, current.currencyCode, digits)}
                    </p>
                    {current.rolloverEnabled && current.rolloverAmountMinor > 0 && (
                      <p className="text-xs text-muted">
                        incl. {formatMoney(current.rolloverAmountMinor, current.currencyCode, digits)} rolled over
                      </p>
                    )}
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-wide text-subtle">Spent</p>
                    <p className="mt-1 text-xl font-semibold text-text num">
                      {formatMoney(progress.data.spentMinor, current.currencyCode, digits)}
                    </p>
                    <p className="text-xs text-muted">
                      {progress.data.daysElapsed}/{progress.data.daysElapsed + progress.data.daysRemaining} days
                    </p>
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-wide text-subtle">Remaining</p>
                    <p className="mt-1 text-xl font-semibold text-text num">
                      {progress.data.overBudget
                        ? formatMoney(progress.data.remainingMinor, current.currencyCode, digits)
                        : formatMoney(progress.data.remainingMinor, current.currencyCode, digits)}
                    </p>
                    <p className="text-xs text-muted">
                      {formatMoney(progress.data.dailyAllowanceMinor, current.currencyCode, digits)}/day left
                    </p>
                  </div>
                </div>

                <div>
                  <BudgetProgressBar
                    utilisationPct={progress.data.utilisationPct}
                    overBudget={progress.data.overBudget}
                  />
                  <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs">
                    <span className="font-medium text-text num">
                      {utilisationPercent(progress.data.utilisationPct)}% used
                    </span>
                    <Badge tone={paceStatusTone(progress.data.paceStatus)}>
                      {PACE_LABELS[progress.data.paceStatus]}
                    </Badge>
                  </div>
                </div>

                {progress.data.categoryBreakdown.length > 0 && (
                  <div>
                    <p className="text-xs uppercase tracking-wide text-subtle">By category</p>
                    <ul className="mt-2 divide-y divide-border text-sm">
                      {progress.data.categoryBreakdown.map((slice) => (
                        <li key={slice.categoryId} className="flex items-center justify-between py-1.5">
                          <span className="flex items-center gap-2 text-text">
                            {slice.color && (
                              <span
                                aria-hidden="true"
                                className="h-2.5 w-2.5 rounded-full"
                                style={{ backgroundColor: slice.color }}
                              />
                            )}
                            {slice.name}
                          </span>
                          <span className="num text-muted">
                            {formatMoney(slice.spentMinor, current.currencyCode, digits)} (
                            {utilisationPercent(slice.sharePct)}%)
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="History" />
          <CardBody>
            {history.isLoading && <p className="text-sm text-muted">Loading history…</p>}
            {history.isError && (
              <p role="alert" className="text-sm text-danger">
                Could not load the history.
              </p>
            )}
            {history.data && slices.length === 0 && (
              <p className="text-sm text-muted">No period history yet.</p>
            )}
            {history.data && slices.length > 0 && (
              <ul className="max-h-80 divide-y divide-border overflow-auto text-sm">
                {slices.map((slice) => (
                  <li key={slice.periodStart} className="flex items-center justify-between py-1.5">
                    <span className="text-muted">
                      {formatBusinessDate(slice.periodStart)}
                      {slice.current ? ' · current' : ''}
                    </span>
                    <span className="num font-medium text-text">
                      {formatMoney(slice.spentMinor, current.currencyCode, digits)}
                      {' / '}
                      {formatMoney(slice.allowanceMinor, current.currencyCode, digits)}
                      <span className={slice.overBudget ? ' text-danger' : ' text-subtle'}>
                        {' '}
                        ({utilisationPercent(slice.utilisationPct)}%)
                      </span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </section>

      <section aria-label="Alerts for this budget">
        <BudgetAlertsPanel budgetId={id} />
      </section>
      {alertRows.length === 0 && alerts.data && (
        <p className="text-sm text-muted">No alerts for this budget.</p>
      )}

      <BudgetFormModal budget={current} open={editing} onClose={() => setEditing(false)} />

      <Modal
        open={confirmingDelete}
        title="Delete budget"
        onClose={() => setConfirmingDelete(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmingDelete(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={removeBudget.isPending}
              onClick={() => {
                removeBudget.mutate(id, {
                  onSuccess: () => {
                    toast.push({ tone: 'success', message: 'Budget deleted' })
                    void navigate('/budgets')
                  },
                  onError: (cause) =>
                    toast.push({
                      tone: 'error',
                      message: cause instanceof ApiError ? cause.message : 'Could not delete the budget',
                    }),
                })
              }}
            >
              Delete budget
            </Button>
          </>
        }
      >
        <p className="text-sm text-text">
          This will remove the budget from every list. Alerts and history are kept. This cannot
          be undone.
        </p>
      </Modal>
    </div>
  )
}