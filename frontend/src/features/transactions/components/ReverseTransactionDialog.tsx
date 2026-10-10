import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { toIsoDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { TRANSACTION_TYPE_LABELS } from '../labels'
import { EMPTY_REVERSE_FORM, reverseFormSchema, type ReverseFormValues } from '../schemas'
import { useReverseTransaction, useReverseTransfer } from '../hooks'
import type { Transaction } from '../types'

export interface ReverseTransactionDialogProps {
  transaction: Transaction | null
  open: boolean
  onClose: () => void
}

/**
 * Confirms a reversal. Transfer legs reverse the whole linked pair through the
 * group endpoint; everything else creates a single mirrored row (D.8).
 */
export function ReverseTransactionDialog({
  transaction,
  open,
  onClose,
}: ReverseTransactionDialogProps) {
  const reverseTransaction = useReverseTransaction()
  const reverseTransfer = useReverseTransfer()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ReverseFormValues>({
    resolver: zodResolver(reverseFormSchema),
    defaultValues: EMPTY_REVERSE_FORM,
  })

  useEffect(() => {
    if (!open) return
    reset({ ...EMPTY_REVERSE_FORM, occurredOn: toIsoDate(new Date()) })
  }, [open, reset])

  const transferGroupId = transaction?.transferGroupId ?? null
  const pending = reverseTransaction.isPending || reverseTransfer.isPending

  const failureMessage = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const onSubmit = handleSubmit((values) => {
    if (transaction === null) return
    const done = () => {
      toast.push({
        tone: 'success',
        message: transferGroupId ? 'Transfer reversed' : 'Transaction reversed',
      })
      onClose()
    }
    const fail = (cause: unknown) =>
      toast.push({
        tone: 'error',
        message: failureMessage(cause, 'Could not reverse the transaction'),
      })
    if (transferGroupId) {
      reverseTransfer.mutate({ transferGroupId, input: values }, { onSuccess: done, onError: fail })
    } else {
      reverseTransaction.mutate(
        { id: transaction.id, input: values },
        { onSuccess: done, onError: fail },
      )
    }
  })

  return (
    <Modal
      open={open}
      title={transferGroupId ? 'Reverse transfer' : 'Reverse transaction'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" type="submit" form="reverse-form" loading={pending}>
            Reverse
          </Button>
        </>
      }
    >
      <form id="reverse-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <p className="text-sm text-muted">
          {transferGroupId
            ? 'This creates mirrored entries for both legs of the transfer. The originals stay in the ledger, marked as reversed, and can no longer be edited.'
            : 'This creates a mirrored entry that cancels the amount out. The original stays in the ledger, marked as reversed, and can no longer be edited.'}
        </p>

        {transaction && (
          <div className="rounded-md border border-border bg-surface-sunken p-4 text-sm">
            <p className="text-text">
              {transaction.description ?? TRANSACTION_TYPE_LABELS[transaction.type]}
            </p>
            <p className="mt-1 text-muted">
              {formatMoney(transaction.amountMinor, transaction.currencyCode, undefined, 'en-US')}
              {transaction.merchant ? ` · ${transaction.merchant}` : ''}
            </p>
          </div>
        )}

        <Input
          label="Reason"
          placeholder="Why is this being reversed?"
          error={errors.reason?.message}
          autoComplete="off"
          {...register('reason')}
        />

        <Input
          label="Reversal date"
          type="date"
          error={errors.occurredOn?.message}
          {...register('occurredOn')}
        />
      </form>
    </Modal>
  )
}
