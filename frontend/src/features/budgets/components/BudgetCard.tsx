import { formatBusinessDate } from '@/lib/dates'
import { formatMoney, fractionDigitsFor } from '@/lib/money'
import { Badge } from '@/components/ui/Badge'
import { Card, CardBody } from '@/components/ui/Card'
import type { Budget, BudgetProgress } from '../types'
import { BUDGET_PERIOD_LABELS, budgetStatusTone } from '../labels'
import { BudgetProgressBar } from './BudgetProgressBar'

export interface BudgetCardProps {
  budget: Budget
  progress: BudgetProgress | null
  onEdit: (budget: Budget) => void
}

export function BudgetCard({ budget, progress, onEdit }: BudgetCardProps) {
  const digits = fractionDigitsFor(budget.currencyCode)
  return (
    <Card>
      <CardBody>
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="font-semibold text-text">{budget.name}</h2>
            <p className="mt-0.5 text-xs text-muted">
              {BUDGET_PERIOD_LABELS[budget.periodType]}
              {budget.category ? ` · ${budget.category.name}` : ' · Overall'}
            </p>
          </div>
          <Badge tone={budgetStatusTone(budget.status)}>{budget.status}</Badge>
        </div>

        <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <div>
            <dt className="text-xs text-muted">Allowance</dt>
            <dd className="mt-0.5 num font-medium text-text">
              {formatMoney(
                progress ? progress.allowanceMinor : budget.amountMinor,
                budget.currencyCode,
                digits,
              )}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Spent</dt>
            <dd className="mt-0.5 num font-medium text-text">
              {progress ? formatMoney(progress.spentMinor, budget.currencyCode, digits) : '—'}
            </dd>
          </div>
        </dl>

        {progress ? (
          <div className="mt-4">
            <BudgetProgressBar utilisationPct={progress.utilisationPct} overBudget={progress.overBudget} />
            <p className="mt-1.5 text-xs text-muted">
              {progress.overBudget ? (
                <span className="font-medium text-danger">
                  Over by {formatMoney(-progress.remainingMinor, budget.currencyCode, digits)}
                </span>
              ) : (
                <>
                  {formatBusinessDate(progress.period.start)} – {formatBusinessDate(progress.period.end)} ·{' '}
                  {progress.daysRemaining} days left
                </>
              )}
            </p>
          </div>
        ) : (
          <p className="mt-4 text-xs text-muted">
            {formatBusinessDate(budget.periodStart)} – {formatBusinessDate(budget.periodEnd)}
          </p>
        )}

        <button
          type="button"
          onClick={() => onEdit(budget)}
          className="mt-4 text-sm text-primary hover:underline"
        >
          Edit
        </button>
      </CardBody>
    </Card>
  )
}