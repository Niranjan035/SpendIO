import { formatBusinessDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody } from '@/components/ui/Card'
import type { Goal } from '../types'
import { GOAL_PRIORITY_LABELS, GOAL_STATUS_LABELS, goalStatusTone, progressPercent } from '../labels'

export interface GoalCardProps {
  goal: Goal
  onEdit: (goal: Goal) => void
}

export function GoalCard({ goal, onEdit }: GoalCardProps) {
  return (
    <Card>
      <CardBody>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="flex items-center gap-2 font-semibold text-text">
              {goal.color && (
                <span
                  aria-hidden="true"
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: goal.color }}
                />
              )}
              <span className="truncate">{goal.icon ? `${goal.icon} ` : ''}{goal.name}</span>
            </h2>
            <p className="mt-0.5 text-xs text-muted">
              {GOAL_PRIORITY_LABELS[goal.priority] ?? 'Priority ' + goal.priority}
              {goal.targetDate ? ` · due ${formatBusinessDate(goal.targetDate)}` : ''}
            </p>
          </div>
          <Badge tone={goalStatusTone(goal.status)}>{GOAL_STATUS_LABELS[goal.status]}</Badge>
        </div>

        <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <div>
            <dt className="text-xs text-muted">Current</dt>
            <dd className="mt-0.5 num font-medium text-text">
              {formatMoney(goal.currentAmountMinor, goal.currencyCode)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Target</dt>
            <dd className="mt-0.5 num font-medium text-text">
              {formatMoney(goal.targetAmountMinor, goal.currencyCode)}
            </dd>
          </div>
        </dl>

        <div className="mt-4">
          <div
            role="progressbar"
            aria-valuenow={progressPercent(goal.progressPct)}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="Goal progress"
            className="h-2 w-full overflow-hidden rounded-full bg-surface-sunken"
          >
            <div
              className="h-full rounded-full bg-primary"
              style={{ width: `${progressPercent(goal.progressPct)}%` }}
            />
          </div>
          <p className="mt-1.5 text-xs text-muted">
            <span className="num font-medium text-text">{progressPercent(goal.progressPct)}%</span>
            {goal.monthlyRequiredMinor !== null ? ` · ${formatMoney(goal.monthlyRequiredMinor, goal.currencyCode)}/mo to be on track` : ''}
          </p>
        </div>

        <button
          type="button"
          onClick={() => onEdit(goal)}
          className="mt-4 text-sm text-primary hover:underline"
        >
          Edit
        </button>
      </CardBody>
    </Card>
  )
}