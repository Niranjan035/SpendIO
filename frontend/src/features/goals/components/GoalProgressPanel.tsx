import { formatBusinessDate } from '@/lib/dates'
import { formatMoney, fractionDigitsFor } from '@/lib/money'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Select } from '@/components/ui/Select'
import type { ChartGranularity, GoalProgress, MilestoneStatus } from '../types'

const GRANULARITY_OPTIONS: ReadonlyArray<{ value: ChartGranularity; label: string }> = [
  { value: 'DAY', label: 'Daily' },
  { value: 'WEEK', label: 'Weekly' },
  { value: 'MONTH', label: 'Monthly' },
  { value: 'YEAR', label: 'Yearly' },
]

export interface GoalProgressPanelProps {
  progress: GoalProgress
  granularity: ChartGranularity
  onGranularityChange: (granularity: ChartGranularity) => void
}

/** Cumulative chart + milestone crossings for the goal detail page (D.11). */
export function GoalProgressPanel({ progress, granularity, onGranularityChange }: GoalProgressPanelProps) {
  const { goal, chart, milestones } = progress
  const digits = fractionDigitsFor(goal.currencyCode)
  const maxCumulative = chart.reduce((max, point) => Math.max(max, point.cumulativeMinor), 0)

  return (
    <Card>
      <CardHeader
        title="Progress"
        actions={
          <div className="w-36">
            <Select
              label="Granularity"
              value={granularity}
              onChange={(event) => onGranularityChange(event.target.value as ChartGranularity)}
            >
              {GRANULARITY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          </div>
        }
      />
      <CardBody>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-subtle">Saved</p>
            <p className="mt-1 text-xl font-semibold text-text num">
              {formatMoney(goal.currentAmountMinor, goal.currencyCode, digits)}
            </p>
          </div>
          <div>
            <p className="text-xs uppercase tracking-wide text-subtle">Target</p>
            <p className="mt-1 text-xl font-semibold text-text num">
              {formatMoney(goal.targetAmountMinor, goal.currencyCode, digits)}
            </p>
          </div>
          <div>
            <p className="text-xs uppercase tracking-wide text-subtle">Remaining</p>
            <p className="mt-1 text-xl font-semibold text-text num">
              {formatMoney(goal.remainingMinor, goal.currencyCode, digits)}
            </p>
          </div>
          <div>
            <p className="text-xs uppercase tracking-wide text-subtle">Monthly needed</p>
            <p className="mt-1 text-xl font-semibold text-text num">
              {goal.monthlyRequiredMinor !== null
                ? formatMoney(goal.monthlyRequiredMinor, goal.currencyCode, digits)
                : '—'}
            </p>
          </div>
        </div>

        {goal.monthlyRequiredMinor !== null && (
          <p className="mt-3 text-sm">
            {goal.onTrack ? (
              <span className="text-success">On track</span>
            ) : (
              <span className="text-warning">Off track</span>
            )}{' '}
            — save {formatMoney(goal.monthlyRequiredMinor, goal.currencyCode, digits)} per month to
            hit {formatMoney(goal.targetAmountMinor, goal.currencyCode, digits)} by{' '}
            {formatBusinessDate(goal.targetDate)}.
          </p>
        )}

        <div className="mt-5">
          <p className="text-xs uppercase tracking-wide text-subtle">Cumulative saved</p>
          {chart.length === 0 ? (
            <p className="mt-2 text-sm text-muted">Add a contribution to start the chart.</p>
          ) : (
            <div className="mt-2 flex items-end gap-1" aria-hidden="true">
              {chart.map((point) => (
                <div
                  key={point.period}
                  className="min-w-0 flex-1 rounded-t bg-primary/60"
                  style={{
                    height: `${maxCumulative > 0 ? Math.max(4, (point.cumulativeMinor / maxCumulative) * 120) : 4}px`,
                  }}
                  title={`${formatBusinessDate(point.period)}: ${formatMoney(point.cumulativeMinor, goal.currencyCode, digits)}`}
                />
              ))}
            </div>
          )}
          <p className="mt-1 text-xs text-muted">
            Buckets of {granularity.toLowerCase()} up to today ·{' '}
            {formatMoney(goal.currentAmountMinor, goal.currencyCode, digits)} saved
          </p>
        </div>

        <div className="mt-5">
          <p className="text-xs uppercase tracking-wide text-subtle">Milestones</p>
          <ul className="mt-2 divide-y divide-border text-sm">
            {milestones.map((milestone) => (
              <MilestoneRow key={milestone.pct} milestone={milestone} currencyCode={goal.currencyCode} />
            ))}
          </ul>
        </div>
      </CardBody>
    </Card>
  )
}

function MilestoneRow({ milestone, currencyCode }: { milestone: MilestoneStatus; currencyCode: string }) {
  const digits = fractionDigitsFor(currencyCode)
  return (
    <li className="flex items-center justify-between py-1.5">
      <span className="flex items-center gap-2 text-text">
        <span className="w-8 font-medium">{milestone.pct}%</span>
        <span className="text-muted">
          {formatMoney(milestone.amountMinor, currencyCode, digits)}
        </span>
      </span>
      {milestone.crossed ? (
        <Badge tone="success">
          Reached {milestone.crossedAt ? formatBusinessDate(milestone.crossedAt) : '—'}
        </Badge>
      ) : (
        <Badge tone="neutral">Not reached yet</Badge>
      )}
    </li>
  )
}