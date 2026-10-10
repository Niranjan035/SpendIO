import { cn } from '@/lib/cn'
import { utilisationPercent } from '../labels'

export interface BudgetProgressBarProps {
  utilisationPct: number
  overBudget?: boolean
  className?: string
}

/** Thin utilisation bar; colour flips to warning at 90% and danger past the budget. */
export function BudgetProgressBar({
  utilisationPct,
  overBudget = false,
  className,
}: BudgetProgressBarProps) {
  const pct = utilisationPercent(utilisationPct)
  const color = overBudget ? 'bg-danger' : pct >= 90 ? 'bg-warning' : 'bg-primary'
  return (
    <div
      role="progressbar"
      aria-valuenow={pct}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label="Budget utilisation"
      className={cn('h-2 w-full overflow-hidden rounded-full bg-surface-sunken', className)}
    >
      <div className={cn('h-full rounded-full transition-all', color)} style={{ width: `${pct}%` }} />
    </div>
  )
}