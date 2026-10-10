import type { RecurringTransaction } from '../types'

export interface FailureBannerProps {
  rule: RecurringTransaction
}

/**
 * Sticky warning for a rule whose retry budget is exhausted (ARCHITECTURE.md J.8):
 * the rule stops generating until it is edited or resumed.
 */
export function FailureBanner({ rule }: FailureBannerProps) {
  const reason = rule.failureReason ?? rule.lastError
  if (rule.status !== 'FAILED') return null
  return (
    <aside
      role="alert"
      className="flex flex-col gap-1 rounded-md border border-danger/25 bg-danger-subtle px-3 py-2 text-sm"
    >
      <p className="font-medium text-danger">
        This rule failed after {rule.retryCount} of {rule.maxRetries} attempts.
      </p>
      {reason && <p className="text-danger/90">{reason}</p>}
      <p className="text-xs text-muted">Edit the rule or resume it to start generating again.</p>
    </aside>
  )
}