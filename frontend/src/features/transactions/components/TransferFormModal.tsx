import { useEffect, useMemo } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { toIsoDate } from '@/lib/dates'
import {
  EMPTY_TRANSFER_FORM,
  toCreateTransferInput,
  transferFormSchema,
  type TransferFormValues,
} from '../schemas'
import { useCreateTransfer, useTransactionFilterOptions } from '../hooks'
import type { AccountOption } from '../types'

export interface TransferFormModalProps {
  open: boolean
  onClose: () => void
}

export function TransferFormModal({ open, onClose }: TransferFormModalProps) {
  const options = useTransactionFilterOptions()
  const create = useCreateTransfer()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<TransferFormValues>({
    resolver: zodResolver(transferFormSchema),
    defaultValues: EMPTY_TRANSFER_FORM,
  })

  const fromAccountId = useWatch({ control, name: 'fromAccountId' })

  useEffect(() => {
    if (!open) return
    reset({ ...EMPTY_TRANSFER_FORM, occurredOn: toIsoDate(new Date()) })
  }, [open, reset])

  const accounts: AccountOption[] = useMemo(
    () => (options.data?.accounts ?? []).filter((account) => account.status === 'ACTIVE'),
    [options.data],
  )

  const fromAccount = accounts.find((candidate) => String(candidate.id) === fromAccountId) ?? null
  const currency = fromAccount?.currencyCode ?? ''

  const failureMessage = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const onSubmit = handleSubmit((values) => {
    const source = accounts.find((candidate) => String(candidate.id) === values.fromAccountId)
    if (!source) return
    create.mutate(toCreateTransferInput(values, source), {
      onSuccess: () => {
        toast.push({ tone: 'success', message: 'Transfer created' })
        onClose()
      },
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: failureMessage(cause, 'Could not create the transfer'),
        }),
    })
  })

  return (
    <Modal
      open={open}
      title="New transfer"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="transfer-form" loading={create.isPending}>
            Create transfer
          </Button>
        </>
      }
    >
      <form id="transfer-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="From account"
            error={errors.fromAccountId?.message}
            {...register('fromAccountId')}
          >
            <option value="">Select an account</option>
            {accounts.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}
              </option>
            ))}
          </Select>
          <Select
            label="To account"
            error={errors.toAccountId?.message}
            {...register('toAccountId')}
          >
            <option value="">Select an account</option>
            {accounts.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}
                {String(option.id) === fromAccountId ? ' (source)' : ''}
              </option>
            ))}
          </Select>
        </div>

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

        <Input
          label="Notes"
          placeholder="Optional notes"
          error={errors.notes?.message}
          autoComplete="off"
          {...register('notes')}
        />

        <p className="text-xs text-muted">
          Both sides of the transfer are created together. Cross-currency transfers freeze each
          side's exchange rate on the transfer date.
        </p>
      </form>
    </Modal>
  )
}
