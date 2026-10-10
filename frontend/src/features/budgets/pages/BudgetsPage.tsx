import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { paths } from '@/routes/paths'
import { BUDGET_PERIODS, BUDGET_STATUSES } from '@/types/enums'
import { BUDGET_PERIOD_LABELS, BUDGET_STATUS_LABELS } from '../labels'
import { useBudgets, useCurrentBudgets } from '../hooks'
import type { Budget, BudgetFilter } from '../types'
import { BudgetCard } from '../components/BudgetCard'
import { BudgetFormModal } from '../components/BudgetFormModal'
import { BudgetAlertsPanel } from '../components/BudgetAlertsPanel'

export function BudgetsPage() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<string>('')
  const [periodType, setPeriodType] = useState<string>('')
  const [editing, setEditing] = useState<Budget | null>(null)
  const [creating, setCreating] = useState(false)

  const filter: BudgetFilter = useMemo(
    () => ({
      status: status === '' ? undefined : (status as BudgetFilter['status']),
      periodType: periodType === '' ? undefined : (periodType as BudgetFilter['periodType']),
      page: 0,
      size: 50,
    }),
    [status, periodType],
  )

  const current = useCurrentBudgets()
  const budgets = useBudgets(filter)
  const all = budgets.data?.content ?? []

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Budgets</h1>
          <p className="mt-1 text-sm text-muted">
            Period limits with live progress, alerts and rollover.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>New budget</Button>
      </header>

      <BudgetAlertsPanel />

      <section aria-label="Current periods">
        <h2 className="text-sm font-semibold text-text">This week and month</h2>
        {current.isLoading && (
          <div className="flex justify-center py-8">
            <LoadingSpinner label="Loading current budgets" />
          </div>
        )}
        {current.isError && (
          <p role="alert" className="text-sm text-danger">
            Could not load current budgets.
          </p>
        )}
        {current.data && current.data.length === 0 && (
          <p className="mt-3 text-sm text-muted">
            No budgets cover today. Create one to start tracking.
          </p>
        )}
        {current.data && current.data.length > 0 && (
          <div className="mt-3 grid gap-4 xl:grid-cols-2">
            {current.data.map((progress) => (
              <button
                key={progress.budget.id}
                type="button"
                onClick={() => navigate(paths.budget(String(progress.budget.id)))}
                className="text-left transition-opacity hover:opacity-90"
              >
                <BudgetCard
                  budget={progress.budget}
                  progress={progress}
                  onEdit={() => navigate(paths.budget(String(progress.budget.id)))}
                />
              </button>
            ))}
          </div>
        )}
      </section>

      <section aria-label="All budgets">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="text-sm font-semibold text-text">All budgets</h2>
          <div className="flex flex-wrap gap-4">
            <div className="w-44">
              <Select
                label="Status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
                aria-label="Filter by status"
              >
                <option value="">All statuses</option>
                {BUDGET_STATUSES.map((option) => (
                  <option key={option} value={option}>
                    {BUDGET_STATUS_LABELS[option]}
                  </option>
                ))}
              </Select>
            </div>
            <div className="w-44">
              <Select
                label="Period"
                value={periodType}
                onChange={(event) => setPeriodType(event.target.value)}
                aria-label="Filter by period"
              >
                <option value="">All periods</option>
                {BUDGET_PERIODS.map((option) => (
                  <option key={option} value={option}>
                    {BUDGET_PERIOD_LABELS[option]}
                  </option>
                ))}
              </Select>
            </div>
          </div>
        </div>

        {budgets.isLoading && (
          <div className="flex justify-center py-8">
            <LoadingSpinner label="Loading budgets" />
          </div>
        )}
        {budgets.isError && (
          <p role="alert" className="text-sm text-danger">
            Could not load budgets.
          </p>
        )}
        {!budgets.isLoading && !budgets.isError && all.length === 0 && (
          <div className="mt-3 rounded-lg border border-border bg-surface p-10 text-center">
            <p className="text-sm text-muted">No budgets yet.</p>
            <p className="mt-1 text-sm text-subtle">Create one to set a spending limit.</p>
          </div>
        )}
        {all.length > 0 && (
          <div className="mt-3 grid gap-4 xl:grid-cols-2">
            {all.map((budget) => (
              <BudgetCard key={budget.id} budget={budget} progress={null} onEdit={setEditing} />
            ))}
          </div>
        )}
      </section>

      <BudgetFormModal
        budget={editing}
        open={creating || editing !== null}
        onClose={() => {
          setCreating(false)
          setEditing(null)
        }}
      />
    </div>
  )
}