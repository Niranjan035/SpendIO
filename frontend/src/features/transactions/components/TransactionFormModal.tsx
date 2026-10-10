import { useEffect, useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { toIsoDate } from '@/lib/dates'
import { fractionDigitsFor, formatMinor } from '@/lib/money'
import { TRANSACTION_STATUSES, TRANSACTION_TYPES } from '@/types/enums'
import {
  TRANSACTION_DIRECTION_LABELS,
  TRANSACTION_STATUS_LABELS,
  TRANSACTION_TYPE_LABELS,
  transactionStatusTone,
  transactionTypeTone,
} from '../labels'
import {
  EMPTY_TRANSACTION_FORM,
  toCreateTransactionInput,
  toUpdateTransactionInput,
  transactionFormSchema,
  type TransactionFormValues,
} from '../schemas'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactionFilterOptions,
  useUpdateTransaction,
} from '../hooks'
import type { AccountOption, Transaction } from '../types'

export interface TransactionFormModalProps {
  transaction: Transaction | null
  open: boolean
  onClose: () => void
}

const CREATE_TYPES = TRANSACTION_TYPES.filter((type) => type !== 'TRANSFER')

export function TransactionFormModal({ transaction, open, onClose }: TransactionFormModalProps) {
  const creating = transaction === null
  const options = useTransactionFilterOptions()
  const create = useCreateTransaction()
  const update = useUpdateTransaction()
  const remove = useDeleteTransaction()
  const toast = useToast()
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionFormSchema),
    defaultValues: EMPTY_TRANSACTION_FORM,
  })

  const type = useWatch({ control, name: 'type' })
  const accountId = useWatch({ control, name: 'accountId' })

  useEffect(() => {
    if (!open) return
    if (creating) {
      reset({ ...EMPTY_TRANSACTION_FORM, occurredOn: toIsoDate(new Date()) })
    } else {
      reset({
        ...EMPTY_TRANSACTION_FORM,
        accountId: String(transaction.account.id),
        type: transaction.type === 'TRANSFER' ? 'EXPENSE' : transaction.type,
        direction: transaction.direction,
        amount: formatMinor(transaction.amountMinor, fractionDigitsFor(transaction.currencyCode)),
        occurredOn: transaction.occurredOn,
        categoryId: transaction.category ? String(transaction.category.id) : '',
        description: transaction.description ?? '',
        merchant: transaction.merchant ?? '',
        payee: transaction.payee ?? '',
        notes: transaction.notes ?? '',
        status: transaction.status,
      })
    }
  }, [open, creating, transaction, reset])

  const accounts: AccountOption[] = useMemo(() => {
    const active = (options.data?.accounts ?? []).filter((account) => account.status === 'ACTIVE')
    if (
      !creating &&
      transaction &&
      !active.some((account) => account.id === transaction.account.id)
    ) {
      return [
        ...active,
        {
          id: transaction.account.id,
          name: transaction.account.name,
          currencyCode: transaction.account.currencyCode,
          status: 'ARCHIVED' as const,
        },
      ]
    }
    return active
  }, [options.data, creating, transaction])

  const account = accounts.find((candidate) => String(candidate.id) === accountId) ?? null
  const currency = account?.currencyCode ?? ''
  const categoriesLoading = options.isPending
  const categoriesError = Boolean(options.error)
  const current = !creating ? transaction : null
  const currentCategory = current?.category ?? null
  const categories = useMemo(() => {
    const base = (options.data?.categories ?? []).filter(
      (category) => category.type === type && category.status === 'ACTIVE',
    )
    // When editing, keep the transaction's own category selectable even if it was
    // archived after the transaction was recorded (mirrors the account behaviour).
    if (
      current &&
      currentCategory &&
      !base.some((category) => category.id === currentCategory.id)
    ) {
      return [
        ...base,
        {
          id: currentCategory.id,
          userId: null,
          parentId: null,
          name: currentCategory.name,
          type: currentCategory.type,
          icon: currentCategory.icon,
          color: currentCategory.color,
          isDefault: false,
          isSystem: false,
          sortOrder: 0,
          excludeFromBudgets: false,
          status: 'ACTIVE' as const,
          archivedAt: null,
          createdAt: current.createdAt,
          updatedAt: current.updatedAt,
        },
      ]
    }
    return base
  }, [options.data, current, currentCategory, type])
  const isAdjustment = type === 'ADJUSTMENT'

  const failureMessage = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const onSubmit = handleSubmit((values) => {
    const target = accounts.find((candidate) => String(candidate.id) === values.accountId)
    if (!target) return
    if (creating) {
      create.mutate(
        {
          input: toCreateTransactionInput(values, target),
          idempotencyKey,
        },
        {
          onSuccess: () => {
            setIdempotencyKey(crypto.randomUUID())
            toast.push({ tone: 'success', message: 'Transaction added' })
            onClose()
          },
          onError: (cause) =>
            toast.push({
              tone: 'error',
              message: failureMessage(cause, 'Could not add the transaction'),
            }),
        },
      )
    } else {
      update.mutate(
        {
          id: transaction.id,
          input: toUpdateTransactionInput(values, target.currencyCode, transaction.version),
        },
        {
          onSuccess: () => {
            toast.push({ tone: 'success', message: 'Transaction updated' })
            onClose()
          },
          onError: (cause) =>
            toast.push({
              tone: 'error',
              message: failureMessage(cause, 'Could not update the transaction'),
            }),
        },
      )
    }
  })

  const onDelete = (): void => {
    if (transaction === null) return
    if (!window.confirm('Delete this transaction?')) return
    remove.mutate(transaction.id, {
      onSuccess: () => {
        toast.push({ tone: 'success', message: 'Transaction deleted' })
        onClose()
      },
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: failureMessage(cause, 'Could not delete the transaction'),
        }),
    })
  }

  return (
    <Modal
      open={open}
      title={creating ? 'New transaction' : 'Edit transaction'}
      onClose={onClose}
      footer={
        creating ? (
          <>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" form="transaction-form" loading={create.isPending}>
              Add transaction
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
            <Button type="submit" form="transaction-form" loading={update.isPending}>
              Save changes
            </Button>
          </>
        )
      }
    >
      <form id="transaction-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
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
              {CREATE_TYPES.map((option) => (
                <option key={option} value={option}>
                  {TRANSACTION_TYPE_LABELS[option]}
                </option>
              ))}
            </Select>
          ) : (
            <div>
              <p className="text-sm font-medium text-text">Type</p>
              <p className="mt-2">
                <Badge tone={transactionTypeTone(type)}>{TRANSACTION_TYPE_LABELS[type]}</Badge>
              </p>
            </div>
          )}
        </div>

        {!isAdjustment && (
          <div className="flex flex-col gap-1.5">
            <Select
              label="Category"
              error={errors.categoryId?.message}
              hint={categoriesLoading ? 'Loading categories…' : undefined}
              disabled={categoriesLoading}
              {...register('categoryId')}
            >
              <option value="">
                {categoriesLoading ? 'Loading categories…' : 'Select a category'}
              </option>
              {!categoriesLoading &&
                categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
            </Select>
            {categoriesError && (
              <p role="alert" className="text-xs text-danger">
                Could not load categories.{' '}
                <button
                  type="button"
                  className="text-danger underline"
                  onClick={() => void options.refetch()}
                >
                  Try again
                </button>
              </p>
            )}
            {!categoriesError && !categoriesLoading && categories.length === 0 && (
              <p className="text-xs text-muted">
                No {TRANSACTION_TYPE_LABELS[type].toLowerCase()} categories yet.{' '}
                <Link to="/categories" className="text-primary underline">
                  Add one
                </Link>
                .
              </p>
            )}
          </div>
        )}

        {isAdjustment && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Select label="Direction" error={errors.direction?.message} {...register('direction')}>
              {(['INFLOW', 'OUTFLOW'] as const).map((direction) => (
                <option key={direction} value={direction}>
                  {TRANSACTION_DIRECTION_LABELS[direction]}
                </option>
              ))}
            </Select>
            {creating ? (
              <Select label="Status" error={errors.status?.message} {...register('status')}>
                {TRANSACTION_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {TRANSACTION_STATUS_LABELS[status]}
                  </option>
                ))}
              </Select>
            ) : (
              <div>
                <p className="text-sm font-medium text-text">Status</p>
                <p className="mt-2">
                  <Badge tone={transactionStatusTone(transaction.status)}>
                    {TRANSACTION_STATUS_LABELS[transaction.status]}
                  </Badge>
                </p>
              </div>
            )}
          </div>
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
          <Input
            label="Date"
            type="date"
            error={errors.occurredOn?.message}
            {...register('occurredOn')}
          />
        </div>

        <Input
          label="Description"
          placeholder="What was this for?"
          error={errors.description?.message}
          autoComplete="off"
          {...register('description')}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Merchant"
            placeholder="Optional merchant"
            error={errors.merchant?.message}
            autoComplete="off"
            {...register('merchant')}
          />
          <Input
            label="Payee"
            placeholder="Optional payee"
            error={errors.payee?.message}
            autoComplete="off"
            {...register('payee')}
          />
        </div>

        <Input
          label="Notes"
          placeholder="Optional notes"
          error={errors.notes?.message}
          autoComplete="off"
          {...register('notes')}
        />

        {!creating && (
          <dl className="grid gap-3 rounded-md border border-border bg-surface-sunken p-4 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-xs text-muted">Direction</dt>
              <dd className="mt-0.5 text-text">
                {TRANSACTION_DIRECTION_LABELS[transaction.direction]}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Merchant</dt>
              <dd className="mt-0.5 text-text">{transaction.merchant ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Payee</dt>
              <dd className="mt-0.5 text-text">{transaction.payee ?? '—'}</dd>
            </div>
          </dl>
        )}

        <p className="text-xs text-muted">
          {creating
            ? 'Amounts freeze their exchange rate on the transaction date.'
            : 'Changing the amount or currency re-freezes the exchange rate for that date.'}
        </p>
      </form>
    </Modal>
  )
}
