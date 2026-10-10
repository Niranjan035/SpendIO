import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { Modal } from '@/components/ui/Modal'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney, fractionDigitsFor } from '@/lib/money'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { GOAL_CONTRIBUTION_METHOD_LABELS, GOAL_PRIORITY_LABELS, GOAL_STATUS_LABELS, goalMethodTone, goalStatusTone } from '../labels'
import {
  useCompleteGoal,
  useDeleteGoal,
  useGoal,
  useGoalProgress,
  useRemoveContribution,
  useReopenGoal,
} from '../hooks'
import type { ChartGranularity } from '../types'
import { GoalFormModal } from '../components/GoalFormModal'
import { GoalProgressPanel } from '../components/GoalProgressPanel'
import { ContributionForm } from '../components/ContributionForm'

export function GoalDetailPage() {
  const { id: rawId } = useParams<{ id: string }>()
  const id = Number(rawId)
  const navigate = useNavigate()
  const toast = useToast()

  const [editing, setEditing] = useState(false)
  const [contributing, setContributing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [granularity, setGranularity] = useState<ChartGranularity>('MONTH')

  const goal = useGoal(id)
  const progress = useGoalProgress(id, granularity)
  const complete = useCompleteGoal()
  const reopen = useReopenGoal()
  const removeGoal = useDeleteGoal()
  const removeContribution = useRemoveContribution()

  const notifyError = (cause: unknown, fallback: string): void => {
    toast.push({ tone: 'error', message: cause instanceof ApiError ? cause.message : fallback })
  }

  if (goal.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted">
        <LoadingSpinner label="Loading goal" /> Loading goal…
      </p>
    )
  }

  if (goal.isError || !goal.data) {
    return (
      <InlineError
        message={goal.error instanceof ApiError ? goal.error.message : 'Could not load the goal.'}
        onRetry={() => void goal.refetch()}
      />
    )
  }

  const current = goal.data
  const digits = fractionDigitsFor(current.currencyCode)
  const recent = progress.data?.contributions ?? []

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            {current.color && (
              <span
                aria-hidden="true"
                className="mt-1.5 h-3 w-3 shrink-0 rounded-full"
                style={{ backgroundColor: current.color }}
              />
            )}
            <h1 className="text-2xl font-semibold text-text">
              {current.icon ? `${current.icon} ` : ''}
              {current.name}
            </h1>
            <Badge tone={goalStatusTone(current.status)}>{GOAL_STATUS_LABELS[current.status]}</Badge>
          </div>
          <p className="mt-1 text-sm text-muted">
            {GOAL_PRIORITY_LABELS[current.priority] ?? 'Priority ' + current.priority}
            {current.targetDate ? ` · target ${formatBusinessDate(current.targetDate)}` : ''}
            {current.daysRemaining !== null ? ` · ${current.daysRemaining} days left` : ''}
          </p>
          {current.description && <p className="mt-1 text-sm text-subtle">{current.description}</p>}
          <button
            type="button"
            onClick={() => navigate('/goals')}
            className="mt-2 text-sm text-primary hover:underline"
          >
            ← All goals
          </button>
        </div>
        <div className="flex flex-wrap gap-2">
          {current.status !== 'COMPLETED' && (
            <Button variant="secondary" size="sm" onClick={() => setContributing(true)}>
              Add contribution
            </Button>
          )}
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            Edit
          </Button>
          {current.status === 'COMPLETED' ? (
            <Button
              variant="secondary"
              size="sm"
              loading={reopen.isPending}
              onClick={() =>
                reopen.mutate(id, {
                  onSuccess: () => toast.push({ tone: 'success', message: 'Goal reopened' }),
                  onError: (cause) => notifyError(cause, 'Could not reopen the goal'),
                })
              }
            >
              Reopen
            </Button>
          ) : (
            <Button
              variant="secondary"
              size="sm"
              loading={complete.isPending}
              onClick={() =>
                complete.mutate(id, {
                  onSuccess: () => toast.push({ tone: 'success', message: 'Goal completed, well done' }),
                  onError: (cause) => notifyError(cause, 'Could not complete the goal'),
                })
              }
            >
              Complete
            </Button>
          )}
          <Button variant="danger" size="sm" onClick={() => setConfirmingDelete(true)}>
            Delete
          </Button>
        </div>
      </header>

      {progress.data ? (
        <GoalProgressPanel
          progress={progress.data}
          granularity={granularity}
          onGranularityChange={setGranularity}
        />
      ) : progress.isLoading ? (
        <p className="text-sm text-muted">Loading progress…</p>
      ) : (
        <p role="alert" className="text-sm text-danger">
          {progress.error instanceof ApiError ? progress.error.message : 'Could not load progress.'}
        </p>
      )}

      <section aria-label="Contributions">
        <Card>
          <CardHeader
            title="Contributions"
            actions={
              current.status === 'COMPLETED' ? (
                <span className="text-xs text-muted">Completed goals keep their history</span>
              ) : undefined
            }
          />
          <CardBody>
            {recent.length === 0 ? (
              <p className="text-sm text-muted">
                No contributions yet. Add the first one to start the chart.
              </p>
            ) : (
              <ul className="divide-y divide-border text-sm">
                {recent.map((entry) => (
                  <li key={entry.id} className="flex items-center justify-between gap-3 py-2">
                    <div className="min-w-0">
                      <p className="flex flex-wrap items-center gap-2 text-text">
                        <span className="num font-medium">
                          {entry.amountMinor < 0 ? '' : '+'}
                          {formatMoney(entry.amountMinor, current.currencyCode, digits)}
                        </span>
                        <Badge tone={goalMethodTone(entry.method)}>
                          {GOAL_CONTRIBUTION_METHOD_LABELS[entry.method]}
                        </Badge>
                        <span className="text-xs text-muted">
                          {formatBusinessDate(entry.contributedOn)}
                          {entry.transactionId !== null ? ` · tx ${entry.transactionId}` : ''}
                        </span>
                      </p>
                      {entry.notes && <p className="mt-0.5 truncate text-xs text-subtle">{entry.notes}</p>}
                    </div>
                    {current.status !== 'COMPLETED' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={removeContribution.isPending}
                        onClick={() =>
                          removeContribution.mutate(
                            { goalId: current.id, contributionId: entry.id },
                            {
                              onSuccess: () => toast.push({ tone: 'success', message: 'Contribution removed' }),
                              onError: (cause) => notifyError(cause, 'Could not remove the contribution'),
                            },
                          )
                        }
                      >
                        Remove
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </section>

      <GoalFormModal goal={current} open={editing} onClose={() => setEditing(false)} />
      <ContributionForm goal={current} open={contributing} onClose={() => setContributing(false)} />

      <Modal
        open={confirmingDelete}
        title="Delete goal"
        onClose={() => setConfirmingDelete(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmingDelete(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={removeGoal.isPending}
              onClick={() => {
                removeGoal.mutate(id, {
                  onSuccess: () => {
                    toast.push({ tone: 'success', message: 'Goal deleted' })
                    void navigate('/goals')
                  },
                  onError: (cause) =>
                    notifyError(cause, 'Could not delete the goal'),
                })
              }}
            >
              Delete goal
            </Button>
          </>
        }
      >
        <p className="text-sm text-text">
          This removes the goal from every list. Contribution history is kept. This cannot be undone.
        </p>
      </Modal>
    </div>
  )
}