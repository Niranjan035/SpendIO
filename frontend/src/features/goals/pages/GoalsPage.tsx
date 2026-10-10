import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { Card, CardBody } from '@/components/ui/Card'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { paths } from '@/routes/paths'
import { formatMoney } from '@/lib/money'
import { GOAL_STATUSES } from '@/types/enums'
import { GOAL_STATUS_LABELS, progressPercent } from '../labels'
import { useGoals, useGoalSummary } from '../hooks'
import type { Goal, GoalFilter } from '../types'
import { GoalCard } from '../components/GoalCard'
import { GoalFormModal } from '../components/GoalFormModal'

export function GoalsPage() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<string>('')
  const [q, setQ] = useState('')
  const [editing, setEditing] = useState<Goal | null>(null)
  const [creating, setCreating] = useState(false)

  const filter: GoalFilter = useMemo(
    () => ({
      status: status === '' ? undefined : (status as GoalFilter['status']),
      q: q === '' ? undefined : q,
      page: 0,
      size: 50,
    }),
    [status, q],
  )

  const summary = useGoalSummary()
  const goals = useGoals(filter)
  const all = goals.data?.content ?? []
  const data = summary.data

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Goals</h1>
          <p className="mt-1 text-sm text-muted">Savings targets, contributions and forecasts.</p>
        </div>
        <Button onClick={() => setCreating(true)}>New goal</Button>
      </header>

      {data && (
        <section aria-label="Overview" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <Card>
            <CardBody>
              <p className="text-xs uppercase tracking-wide text-subtle">Active</p>
              <p className="mt-1 text-2xl font-semibold text-text">{data.activeCount}</p>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <p className="text-xs uppercase tracking-wide text-subtle">Completed</p>
              <p className="mt-1 text-2xl font-semibold text-text">{data.completedCount}</p>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <p className="text-xs uppercase tracking-wide text-subtle">Saved</p>
              <p className="mt-1 text-2xl font-semibold text-text num">
                {formatMoney(data.totalCurrentBaseMinor, data.baseCurrencyCode)}
              </p>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <p className="text-xs uppercase tracking-wide text-subtle">Of the target</p>
              <p className="mt-1 text-2xl font-semibold text-text num">
                {progressPercent(data.overallProgressPct)}%
              </p>
              <p className="mt-1 text-xs text-muted num">
                {formatMoney(data.remainingBaseMinor, data.baseCurrencyCode)} to go
              </p>
            </CardBody>
          </Card>
        </section>
      )}

      <section aria-label="Goals">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="text-sm font-semibold text-text">All goals</h2>
          <div className="flex flex-wrap gap-4">
            <div className="w-48">
              <Input
                label="Search"
                placeholder="Name"
                value={q}
                onChange={(event) => setQ(event.target.value)}
                aria-label="Search goals"
              />
            </div>
            <div className="w-44">
              <Select
                label="Status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
                aria-label="Filter by status"
              >
                <option value="">All statuses</option>
                {GOAL_STATUSES.map((option) => (
                  <option key={option} value={option}>
                    {GOAL_STATUS_LABELS[option]}
                  </option>
                ))}
              </Select>
            </div>
          </div>
        </div>

        {goals.isLoading && (
          <div className="flex justify-center py-8">
            <LoadingSpinner label="Loading goals" />
          </div>
        )}
        {goals.isError && (
          <p role="alert" className="text-sm text-danger">
            Could not load goals.
          </p>
        )}
        {!goals.isLoading && !goals.isError && all.length === 0 && (
          <div className="mt-3 rounded-lg border border-border bg-surface p-10 text-center">
            <p className="text-sm text-muted">No goals yet.</p>
            <p className="mt-1 text-sm text-subtle">Create one to start saving toward something.</p>
          </div>
        )}
        {all.length > 0 && (
          <div className="mt-3 grid gap-4 xl:grid-cols-2">
            {all.map((goal) => (
              <div
                key={goal.id}
                onClick={() => navigate(paths.goal(String(goal.id)))}
                className="cursor-pointer transition-opacity hover:opacity-90"
              >
                <GoalCard goal={goal} onEdit={() => navigate(paths.goal(String(goal.id)))} />
              </div>
            ))}
          </div>
        )}
      </section>

      <GoalFormModal
        goal={editing}
        open={creating || editing !== null}
        onClose={() => {
          setCreating(false)
          setEditing(null)
        }}
      />
    </div>
  )
}