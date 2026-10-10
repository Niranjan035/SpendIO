import { useEffect, useMemo } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { toIsoDate } from '@/lib/dates'
import { useTransactionFilterOptions } from '@/features/transactions/hooks'
import { TRANSACTION_TYPE_LABELS, transactionTypeTone } from '@/features/transactions/labels'
import { RECURRENCE_FREQUENCIES } from '@/types/enums'
import { RECURRENCE_FREQUENCY_LABELS } from '../labels'
import { previewDates, type PreviewRule } from '../preview'
import {
  EMPTY_RECURRING_FORM,
  recurringFormDefaults,
  recurringFormSchema,
  toCreateRecurringInput,
  toUpdateRecurringInput,
  type RecurringFormValues,
} from '../schemas'
import { useCreateRecurring, useDeleteRecurring, useUpdateRecurring } from '../hooks'
import type { RecurringTransaction } from '../types'
import type { AccountOption } from '@/features/transactions/types'
import { SchedulePreview } from './SchedulePreview'

export interface RecurringFormModalProps {
  rule: RecurringTransaction | null
  open: boolean
  onClose: () => void
}

export function RecurringFormModal({ rule, open, onClose }: RecurringFormModalProps) {
  const creating = rule === null
  const options = useTransactionFilterOptions()
  const create = useCreateRecurring()
  const update = useUpdateRecurring()
  const remove = useDeleteRecurring()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<RecurringFormValues>({
    resolver: zodResolver(recurringFormSchema),
    defaultValues: EMPTY_RECURRING_FORM,
  })

  const type = useWatch({ control, name: 'type' })
  const accountId = useWatch({ control, name: 'accountId' })
  const frequency = useWatch({ control, name: 'frequency' })

  const scheduleWatchers = useWatch({
    control,
    name: [
      'frequency',
      'intervalCount',
      'dayOfMonth',
      'dayOfWeek',
      'skipWeekends',
      'startDate',
      'endDate',
      'targetOccurrenceDate',
    ],
  })

  useEffect(() => {
    if (!open) return
    if (creating) {
      reset({ ...EMPTY_RECURRING_FORM, startDate: toIsoDate(new Date()) })
    } else {
      reset(recurringFormDefaults(rule))
    }
  }, [open, creating, rule, reset])

  const accounts: AccountOption[] = useMemo(() => {
    const active = (options.data?.accounts ?? []).filter((account) => account.status === 'ACTIVE')
    if (
      !creating &&
      rule &&
      !active.some((account) => account.id === rule.account.id)
    ) {
      return [
        ...active,
        {
          id: rule.account.id,
          name: rule.account.name,
          currencyCode: rule.account.currencyCode,
          status: 'ARCHIVED' as const,
        },
      ]
    }
    return active
  }, [options.data, creating, rule])

  const account = accounts.find((candidate) => String(candidate.id) === accountId) ?? null
  const currency = account?.currencyCode ?? ''
  const categories = (options.data?.categories ?? []).filter(
    (category) => category.type === type && category.status === 'ACTIVE',
  )
  const transfer = type === 'TRANSFER'

  const intervalHint =
    frequency === 'WEEKLY'
      ? 'weeks'
      : frequency === 'MONTHLY'
        ? 'months'
        : frequency === 'YEARLY'
          ? 'years'
          : 'days'

  const previewRule: PreviewRule | null = useMemo(() => {
    if (!open) return null
    const [freq, interval, dayOfMonth, dayOfWeek, skipWeekends, startDate, endDate, targetDate] =
      scheduleWatchers as [
        RecurringFormValues['frequency'],
        string,
        string,
        string,
        boolean,
        string,
        string,
        string,
      ]
    if (startDate === '') return null
    return {
      startDate,
      endDate: endDate === '' ? null : endDate,
      frequency: freq,
      intervalCount: Number(interval),
      dayOfMonth: dayOfMonth === '' ? null : Number(dayOfMonth),
      dayOfWeek: dayOfWeek === '' ? null : Number(dayOfWeek),
      skipWeekends,
      targetOccurrenceDate: targetDate === '' ? null : targetDate,
    }
  }, [open, scheduleWatchers])

  const dates = useMemo(
    () => (previewRule ? previewDates(previewRule, toIsoDate(new Date()), 5) : []),
    [previewRule],
  )

  const willRun = rule ? rule.status === 'ACTIVE' : true

  const failureMessage = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const onSubmit = handleSubmit((values) => {
    const target = accounts.find((candidate) => String(candidate.id) === values.accountId)
    if (!target) return
    if (creating) {
      create.mutate(
        toCreateRecurringInput(values, target),
        {
          onSuccess: () => {
            toast.push({ tone: 'success', message: 'Recurring rule added' })
            onClose()
          },
          onError: (cause) =>
            toast.push({
              tone: 'error',
              message: failureMessage(cause, 'Could not add the recurring rule'),
            }),
        },
      )
    } else {
      update.mutate(
        {
          id: rule.id,
          input: toUpdateRecurringInput(values, target, 0),
        },
        {
          onSuccess: () => {
            toast.push({ tone: 'success', message: 'Recurring rule updated' })
            onClose()
          },
          onError: (cause) =>
            toast.push({
              tone: 'error',
              message: failureMessage(cause, 'Could not update the recurring rule'),
            }),
        },
      )
    }
  })

  const onDelete = (): void => {
    if (rule === null) return
    if (!window.confirm('Delete this recurring rule? Ledger history is kept.')) return
    remove.mutate(rule.id, {
      onSuccess: () => {
        toast.push({ tone: 'success', message: 'Recurring rule deleted' })
        onClose()
      },
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: failureMessage(cause, 'Could not delete the recurring rule'),
        }),
    })
  }

  return (
    <Modal
      open={open}
      title={creating ? 'New recurring rule' : 'Edit recurring rule'}
      onClose={onClose}
      footer={
        creating ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" form="recurring-form" loading={create.isPending}>
              Add rule
            </Button>
          </>
        ) : (
          <>
            <Button variant="danger" onClick={onDelete} disabled={remove.isPending}>
              Delete
            </Button>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" form="recurring-form" loading={update.isPending}>
              Save changes
            </Button>
          </>
        )
      }
    >
      <form id="recurring-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Account" error={errors.accountId?.message} {...register('accountId')}>
            <option value="">Select an account</option>
            {accounts.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}
                {option.status === 'ARCHIVED' ? ' (archived)' : ''}
              </option>
            ))}
          </Select>
          {creating ? (
            <Select label="Type" error={errors.type?.message} {...register('type')}>
              {(['INCOME', 'EXPENSE', 'TRANSFER'] as const).map((option) => (
                <option key={option} value={option}>
                  {TRANSACTION_TYPE_LABELS[option]}
                </option>
              ))}
            </Select>
          ) : (
            <div>
              <p className="text-sm font-medium text-text">Type</p>
              <p className="mt-2">
                <Badge tone={transactionTypeTone(rule.type)}>{TRANSACTION_TYPE_LABELS[rule.type]}</Badge>
              </p>
            </div>
          )}
        </div>

        {transfer && (
          <Select label="Destination account" error={errors.toAccountId?.message} {...register('toAccountId')}>
            <option value="">Select a destination account</option>
            {accounts.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}
              </option>
            ))}
          </Select>
        )}

        {!transfer && (
          <Select label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
            <option value="">Select a category</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Amount"
            placeholder="0.00"
            numeric
            inputMode="decimal"
            hint={currency ? `Amount in ${currency}` : undefined}
            error={errors.amount?.message}
            {...register('amount')}
          />
          <Select label="Repeats" error={errors.frequency?.message} {...register('frequency')}>
            {RECURRENCE_FREQUENCIES.map((option) => (
              <option key={option} value={option}>
                {RECURRENCE_FREQUENCY_LABELS[option]}
              </option>
            ))}
          </Select>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Every"
            inputMode="numeric"
            hint={frequency ? intervalHint : undefined}
            error={errors.intervalCount?.message}
            {...register('intervalCount')}
          />
          {frequency === 'MONTHLY' && (
            <Input
              label="Day of month"
              placeholder="e.g. 15"
              inputMode="numeric"
              hint="1–31"
              error={errors.dayOfMonth?.message}
              {...register('dayOfMonth')}
            />
          )}
          {frequency === 'WEEKLY' && (
            <Select label="Day of week" error={errors.dayOfWeek?.message} {...register('dayOfWeek')}>
              {DAY_OF_WEEK_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
          {frequency === 'CUSTOM' && (
            <Input
              label="First payday"
              type="date"
              error={errors.targetOccurrenceDate?.message}
              {...register('targetOccurrenceDate')}
            />
          )}
        </div>

        {frequency === 'DAILY' || frequency === 'WEEKLY' || frequency === 'MONTHLY' ? (
          <label className="flex items-center gap-2 text-sm text-text">
            <input type="checkbox" className="h-4 w-4 rounded border border-border-strong" {...register('skipWeekends')} />
            Skip weekends (shift Sat → Fri, Sun → Mon)
          </label>
        ) : null}

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Start date"
            type="date"
            error={errors.startDate?.message}
            {...register('startDate')}
          />
          <Input
            label="End date"
            type="date"
            hint="Optional"
            error={errors.endDate?.message}
            {...register('endDate')}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="flex items-center gap-2 text-sm text-text">
            <input type="checkbox" className="h-4 w-4 rounded border border-border-strong" {...register('autoPost')} />
            Post automatically
          </label>
          <Input
            label="Retries before failure"
            inputMode="numeric"
            hint={rule ? `${rule.retryCount} used so far` : '1–99'}
            error={errors.maxRetries?.message}
            {...register('maxRetries')}
          />
        </div>

        <Input
          label="Description"
          placeholder="Rent, salary, subscription…"
          error={errors.description?.message}
          autoComplete="off"
          {...register('description')}
        />

        <Input
          label="Notes"
          placeholder="Optional notes"
          error={errors.notes?.message}
          autoComplete="off"
          {...register('notes')}
        />

        <SchedulePreview dates={dates} willRun={willRun} />
      </form>
    </Modal>
  )
}

const DAY_OF_WEEK_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: '1', label: 'Monday' },
  { value: '2', label: 'Tuesday' },
  { value: '3', label: 'Wednesday' },
  { value: '4', label: 'Thursday' },
  { value: '5', label: 'Friday' },
  { value: '6', label: 'Saturday' },
  { value: '7', label: 'Sunday' },
]