import { useEffect, useMemo } from 'react'
import { useForm, useWatch } from 'react-hook-form'
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
import { formatBusinessDate } from '@/lib/dates'
import { BUDGET_PERIODS, type BudgetStatus } from '@/types/enums'
import { BUDGET_PERIOD_LABELS } from '../labels'
import {
  budgetFormDefaults,
  budgetFormSchema,
  canonicalPeriod,
  EMPTY_BUDGET_FORM,
  toCreateBudgetInput,
  toUpdateBudgetInput,
  type BudgetFormValues,
} from '../schemas'
import { useCreateBudget, useDeleteBudget, useUpdateBudget } from '../hooks'
import type { Budget } from '../types'

export interface BudgetFormModalProps {
  budget: Budget | null
  open: boolean
  onClose: () => void
}

export function BudgetFormModal({ budget, open, onClose }: BudgetFormModalProps) {
  const creating = budget === null
  const baseCurrency = useAuthStore((s) => s.user?.baseCurrency ?? '')
  const options = useTransactionFilterOptions()
  const currencies = useCurrencies()
  const create = useCreateBudget()
  const update = useUpdateBudget()
  const remove = useDeleteBudget()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<BudgetFormValues>({
    resolver: zodResolver(budgetFormSchema),
    defaultValues: EMPTY_BUDGET_FORM,
  })

  const periodType = useWatch({ control, name: 'periodType' })
  const currencyCode = useWatch({ control, name: 'currencyCode' })

  useEffect(() => {
    if (!open) return
    if (creating) {
      reset({ ...EMPTY_BUDGET_FORM, currencyCode: baseCurrency })
    } else {
      reset(budgetFormDefaults(budget))
    }
  }, [open, creating, budget, reset, baseCurrency])

  const categories = useMemo(() => {
    const active = (options.data?.categories ?? []).filter(
      (category) => category.type === 'EXPENSE' && category.status === 'ACTIVE',
    )
    if (
      !creating &&
      budget?.category &&
      !active.some((category) => category.id === budget.category?.id)
    ) {
      return [
        ...active,
        {
          id: budget.category.id,
          name: budget.category.name,
          type: 'EXPENSE' as const,
          status: 'ARCHIVED' as const,
          parentId: null,
          color: budget.category.color,
          icon: budget.category.icon,
        },
      ]
    }
    return active
  }, [options.data, creating, budget])

  const failed = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const onSubmit = handleSubmit((values) => {
    if (creating) {
      create.mutate(toCreateBudgetInput(values, null), {
        onSuccess: () => {
          toast.push({ tone: 'success', message: 'Budget created' })
          onClose()
        },
        onError: (cause) =>
          toast.push({ tone: 'error', message: failed(cause, 'Could not create the budget') }),
      })
    } else {
      update.mutate(
        { id: budget.id, input: toUpdateBudgetInput(values, budget) },
        {
          onSuccess: () => {
            toast.push({ tone: 'success', message: 'Budget updated' })
            onClose()
          },
          onError: (cause) =>
            toast.push({ tone: 'error', message: failed(cause, 'Could not update the budget') }),
        },
      )
    }
  })

  const onDelete = (): void => {
    if (budget === null) return
    if (!window.confirm('Delete this budget? Alerts and history are kept.')) return
    remove.mutate(budget.id, {
      onSuccess: () => {
        toast.push({ tone: 'success', message: 'Budget deleted' })
        onClose()
      },
      onError: (cause) =>
        toast.push({ tone: 'error', message: failed(cause, 'Could not delete the budget') }),
    })
  }

  const custom = periodType === 'CUSTOM'
  const canonical = custom ? null : canonicalPeriod(periodType, new Date())

  return (
    <Modal
      open={open}
      title={creating ? 'New budget' : 'Edit budget'}
      onClose={onClose}
      footer={
        creating ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" form="budget-form" loading={create.isPending}>
              Create budget
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
            <Button type="submit" form="budget-form" loading={update.isPending}>
              Save changes
            </Button>
          </>
        )
      }
    >
      <form id="budget-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <Input
          label="Name"
          placeholder="Groceries, rent, travel…"
          error={errors.name?.message}
          autoComplete="off"
          {...register('name')}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Period" error={errors.periodType?.message} {...register('periodType')}>
            {BUDGET_PERIODS.map((option) => (
              <option key={option} value={option}>
                {BUDGET_PERIOD_LABELS[option]}
              </option>
            ))}
          </Select>
          <Select
            label="Category"
            hint="Overall covers every expense category"
            error={errors.categoryId?.message}
            {...register('categoryId')}
          >
            <option value="">Overall</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
                {category.status === 'ARCHIVED' ? ' (archived)' : ''}
              </option>
            ))}
          </Select>
        </div>

        {custom ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label="Start date"
              type="date"
              error={errors.customPeriodStart?.message}
              {...register('customPeriodStart')}
            />
            <Input
              label="End date"
              type="date"
              error={errors.customPeriodEnd?.message}
              {...register('customPeriodEnd')}
            />
          </div>
        ) : (
          <p className="rounded-md border border-border bg-surface-sunken px-3 py-2 text-xs text-muted">
            Period window: {canonical ? `${formatBusinessDate(canonical.start)} – ${formatBusinessDate(canonical.end)}` : ''}
          </p>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Allowance"
            placeholder="0.00"
            numeric
            inputMode="decimal"
            hint={currencyCode ? `Amount in ${currencyCode.toUpperCase()}` : undefined}
            error={errors.amount?.message}
            {...register('amount')}
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
          <label className="flex items-center gap-2 text-sm text-text">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border border-border-strong"
              {...register('rolloverEnabled')}
            />
            Carry unused allowance into the next period
          </label>
          <label className="flex items-center gap-2 text-sm text-text">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border border-border-strong"
              {...register('includeSubcategories')}
            />
            Include subcategories
          </label>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="flex items-center gap-2 text-sm text-text">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border border-border-strong"
              {...register('alertEnabled')}
            />
            Send alerts
          </label>
          <Input
            label="Alert threshold"
            inputMode="numeric"
            hint="% of the allowance"
            error={errors.alertThresholdPct?.message}
            {...register('alertThresholdPct')}
          />
        </div>

        {!creating && (
          <Select label="Status" error={errors.status?.message} {...register('status')}>
            {(['ACTIVE', 'ARCHIVED'] as readonly BudgetStatus[]).map((option) => (
              <option key={option} value={option}>
                {option === 'ACTIVE' ? 'Active' : 'Archived'}
              </option>
            ))}
          </Select>
        )}
      </form>
    </Modal>
  )
}