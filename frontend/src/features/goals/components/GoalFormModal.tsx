import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { useAuthStore } from '@/features/auth/auth.store'
import { useTransactionFilterOptions } from '@/features/transactions/hooks'
import { useCurrencies } from '@/features/settings/hooks'
import { toIsoDate } from '@/lib/dates'
import { GOAL_PRIORITY_LABELS } from '../labels'
import {
  EMPTY_GOAL_FORM,
  goalFormDefaults,
  goalFormSchema,
  GOAL_PRIORITY_VALUES,
  toCreateGoalInput,
  toUpdateGoalInput,
  type GoalFormValues,
} from '../schemas'
import { useCreateGoal, useDeleteGoal, useUpdateGoal } from '../hooks'
import type { Goal } from '../types'

export interface GoalFormModalProps {
  goal: Goal | null
  open: boolean
  onClose: () => void
}

export function GoalFormModal({ goal, open, onClose }: GoalFormModalProps) {
  const creating = goal === null
  const baseCurrency = useAuthStore((s) => s.user?.baseCurrency ?? '')
  const options = useTransactionFilterOptions()
  const currencies = useCurrencies()
  const create = useCreateGoal()
  const update = useUpdateGoal()
  const remove = useDeleteGoal()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<GoalFormValues>({
    resolver: zodResolver(goalFormSchema),
    defaultValues: EMPTY_GOAL_FORM,
  })

  useEffect(() => {
    if (!open) return
    if (creating) {
      reset({ ...EMPTY_GOAL_FORM, currencyCode: baseCurrency })
    } else {
      reset(goalFormDefaults(goal))
    }
  }, [open, creating, goal, reset, baseCurrency])

  const failed = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const onSubmit = handleSubmit((values) => {
    if (creating) {
      create.mutate(toCreateGoalInput(values), {
        onSuccess: () => {
          toast.push({ tone: 'success', message: 'Goal created' })
          onClose()
        },
        onError: (cause) =>
          toast.push({ tone: 'error', message: failed(cause, 'Could not create the goal') }),
      })
    } else {
      update.mutate(
        { id: goal.id, input: toUpdateGoalInput(values, goal) },
        {
          onSuccess: () => {
            toast.push({ tone: 'success', message: 'Goal updated' })
            onClose()
          },
          onError: (cause) =>
            toast.push({ tone: 'error', message: failed(cause, 'Could not update the goal') }),
        },
      )
    }
  })

  const onDelete = (): void => {
    if (goal === null) return
    if (!window.confirm('Delete this goal? Contribution history is kept.')) return
    remove.mutate(goal.id, {
      onSuccess: () => {
        toast.push({ tone: 'success', message: 'Goal deleted' })
        onClose()
      },
      onError: (cause) =>
        toast.push({ tone: 'error', message: failed(cause, 'Could not delete the goal') }),
    })
  }

  const accounts = (options.data?.accounts ?? []).filter((account) => account.status === 'ACTIVE')

  return (
    <Modal
      open={open}
      title={creating ? 'New goal' : 'Edit goal'}
      onClose={onClose}
      footer={
        creating ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" form="goal-form" loading={create.isPending}>
              Create goal
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
            <Button type="submit" form="goal-form" loading={update.isPending}>
              Save changes
            </Button>
          </>
        )
      }
    >
      <form id="goal-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <Input
          label="Name"
          placeholder="Emergency fund, holiday, laptop…"
          error={errors.name?.message}
          autoComplete="off"
          {...register('name')}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Target amount"
            placeholder="0.00"
            numeric
            inputMode="decimal"
            error={errors.targetAmount?.message}
            {...register('targetAmount')}
          />
          <Select label="Currency" error={errors.currencyCode?.message} {...register('currencyCode')}>
            {(currencies.data ?? []).map((currency) => (
              <option key={currency.code} value={currency.code}>
                {currency.code} — {currency.name}
              </option>
            ))}
          </Select>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Target date"
            type="date"
            min={toIsoDate(new Date())}
            hint="Optional"
            error={errors.targetDate?.message}
            {...register('targetDate')}
          />
          <Select label="Priority" hint="0 low – 5 critical" error={errors.priority?.message} {...register('priority')}>
            {GOAL_PRIORITY_VALUES.map((option) => (
              <option key={option} value={option}>
                {GOAL_PRIORITY_LABELS[Number(option)]} ({option})
              </option>
            ))}
          </Select>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Linked account"
            hint="Used for transfer contributions"
            error={errors.linkedAccountId?.message}
            {...register('linkedAccountId')}
          >
            <option value="">No linked account</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </Select>
          <Input
            label="Milestone alert"
            inputMode="numeric"
            hint="Notify when this % is reached"
            error={errors.milestonePct?.message}
            {...register('milestonePct')}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Icon"
            placeholder="🚗"
            hint="Optional emoji or short label"
            error={errors.icon?.message}
            autoComplete="off"
            {...register('icon')}
          />
          <Input
            label="Colour"
            placeholder="#1E88E5"
            hint="Optional hex colour"
            error={errors.color?.message}
            autoComplete="off"
            {...register('color')}
          />
        </div>

        <Input
          label="Description"
          placeholder="Why you are saving for this"
          error={errors.description?.message}
          autoComplete="off"
          {...register('description')}
        />

        {!creating && (
          <Select label="Status" error={errors.status?.message} {...register('status')}>
            {(['ACTIVE', 'PAUSED', 'CANCELLED'] as const).map((option) => (
              <option key={option} value={option}>
                {option === 'ACTIVE' ? 'Active' : option === 'PAUSED' ? 'Paused' : 'Cancelled'}
              </option>
            ))}
          </Select>
        )}
      </form>
    </Modal>
  )
}