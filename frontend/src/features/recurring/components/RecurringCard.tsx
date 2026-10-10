import { useState } from 'react'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Modal } from '@/components/ui/Modal'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { TRANSACTION_TYPE_LABELS, transactionTypeTone } from '@/features/transactions/labels'
import { RECURRING_STATUS_LABELS, recurringEveryLabel, recurringStatusTone } from '../labels'
import { useRunNowRecurring } from '../hooks'
import type { RecurringTransaction } from '../types'
import { FailureBanner } from './FailureBanner'
import { PauseResumeControl } from './PauseResumeControl'
import { RunHistoryTable } from './RunHistoryTable'

export interface RecurringCardProps {
  rule: RecurringTransaction
  onEdit: (rule: RecurringTransaction) => void
}

/** One rule in the recurring list: amount, schedule, next run and lifecycle controls. */
export function RecurringCard({ rule, onEdit }: RecurringCardProps) {
  const [historyOpen, setHistoryOpen] = useState(false)
  const runNow = useRunNowRecurring()
  const toast = useToast()

  const onRunNow = (): void => {
    runNow.mutate(
      { id: rule.id, idempotencyKey: crypto.randomUUID() },
      {
        onSuccess: (run) =>
          toast.push({
            tone: 'success',
            message: run.transactionId
              ? `Posted transaction #${run.transactionId}`
              : 'Occurrence ran without posting',
          }),
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: cause instanceof ApiError ? cause.message : 'Could not run this occurrence',
          }),
      },
    )
  }

  const destination = rule.toAccount ? ` → ${rule.toAccount.name}` : ''

  return (
    <>
      <Card>
        <CardHeader
          title={
            <span className="flex items-center justify-between gap-2">
              <span className="truncate font-semibold">
                {rule.description ?? `${TRANSACTION_TYPE_LABELS[rule.type]} rule`}
              </span>
              <Badge tone={recurringStatusTone(rule.status)}>
                {RECURRING_STATUS_LABELS[rule.status]}
              </Badge>
            </span>
          }
          description={`${rule.account.name}${destination}`}
          actions={
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => setHistoryOpen(true)}>
                History
              </Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={onRunNow}
                disabled={rule.status !== 'ACTIVE' || runNow.isPending}
              >
                Run now
              </Button>
              <Button size="sm" variant="secondary" onClick={() => onEdit(rule)}>
                Edit
              </Button>
            </div>
          }
        />
        <CardBody className="flex flex-col gap-3">
          <FailureBanner rule={rule} />
          <div className="flex items-baseline justify-between gap-4">
            <p className="text-2xl font-semibold text-text num">
              {formatMoney(rule.amountMinor, rule.currencyCode)}
            </p>
            <div className="flex items-center gap-2 text-sm">
              <Badge tone={transactionTypeTone(rule.type)}>{TRANSACTION_TYPE_LABELS[rule.type]}</Badge>
              {rule.category && <span className="text-muted">{rule.category.name}</span>}
            </div>
          </div>
          <dl className="grid gap-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wide text-subtle">Schedule</dt>
              <dd className="mt-0.5 text-text">
                {recurringEveryLabel(rule.frequency, rule.intervalCount)}
                {rule.dayOfMonth && rule.frequency === 'MONTHLY' ? ` on the ${ordinal(rule.dayOfMonth)}` : ''}
                {rule.skipWeekends ? ' (skip weekends)' : ''}
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-subtle">Next run</dt>
              <dd className="mt-0.5 text-text">
                {formatBusinessDate(rule.nextRunDate)}
                {rule.retryCount > 0 && rule.status === 'ACTIVE' && (
                  <span className="ml-2 text-xs text-warning">{rule.retryCount} retries used</span>
                )}
              </dd>
            </div>
          </dl>
          <PauseResumeControl rule={rule} />
        </CardBody>
      </Card>

      <Modal open={historyOpen} title="Run history" onClose={() => setHistoryOpen(false)}>
        <RunHistoryTable ruleId={rule.id} />
      </Modal>
    </>
  )
}

function ordinal(value: number): string {
  const abs = Math.abs(value)
  if (abs % 100 >= 11 && abs % 100 <= 13) return `${value}th`
  switch (abs % 10) {
    case 1:
      return `${value}st`
    case 2:
      return `${value}nd`
    case 3:
      return `${value}rd`
    default:
      return `${value}th`
  }
}