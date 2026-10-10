import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { useCancelRecurring, usePauseRecurring, useResumeRecurring } from '../hooks'
import type { RecurringTransaction } from '../types'

export interface PauseResumeControlProps {
  rule: RecurringTransaction
}

/**
 * Lifecycle controls for one rule (ARCHITECTURE.md E.9): pause hides the next run,
 * resume re-anchors the schedule, cancel is terminal. State flips refresh the list
 * through the shared react-query keys.
 */
export function PauseResumeControl({ rule }: PauseResumeControlProps) {
  const pause = usePauseRecurring()
  const resume = useResumeRecurring()
  const cancel = useCancelRecurring()
  const toast = useToast()

  const failure = (cause: unknown, fallback: string): void => {
    toast.push({ tone: 'error', message: cause instanceof ApiError ? cause.message : fallback })
  }

  const onPause = (): void =>
    pause.mutate(rule.id, {
      onSuccess: () => toast.push({ tone: 'success', message: 'Rule paused' }),
      onError: (cause) => failure(cause, 'Could not pause the rule'),
    })

  const onResume = (): void =>
    resume.mutate(rule.id, {
      onSuccess: () => toast.push({ tone: 'success', message: 'Rule resumed' }),
      onError: (cause) => failure(cause, 'Could not resume the rule'),
    })

  const onCancel = (): void => {
    if (!window.confirm('Cancel this recurring rule? This cannot be undone.')) return
    cancel.mutate(rule.id, {
      onSuccess: () => toast.push({ tone: 'success', message: 'Rule cancelled' }),
      onError: (cause) => failure(cause, 'Could not cancel the rule'),
    })
  }

  const busy = pause.isPending || resume.isPending || cancel.isPending

  return (
    <div className="flex flex-wrap gap-2">
      {rule.status === 'ACTIVE' && (
        <Button size="sm" variant="secondary" onClick={onPause} disabled={busy}>
          Pause
        </Button>
      )}
      {(rule.status === 'PAUSED' || rule.status === 'FAILED') && (
        <Button size="sm" variant="secondary" onClick={onResume} disabled={busy}>
          Resume
        </Button>
      )}
      {(rule.status === 'ACTIVE' || rule.status === 'PAUSED') && (
        <Button size="sm" variant="ghost" onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
      )}
    </div>
  )
}