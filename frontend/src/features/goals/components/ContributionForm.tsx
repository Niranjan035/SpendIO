import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { toIsoDate } from '@/lib/dates'
import { fractionDigitsFor, parseAmountToMinor } from '@/lib/money'
import { GOAL_CONTRIBUTION_METHOD_LABELS } from '../labels'
import {
  contributionFormSchema,
  EMPTY_CONTRIBUTION_FORM,
  toCreateContributionInput,
  type ContributionFormValues,
} from '../schemas'
import { useAddContribution } from '../hooks'
import type { Goal } from '../types'

export interface ContributionFormProps {
  goal: Goal
  open: boolean
  onClose: () => void
}

/** Deposit or withdrawal against a goal (D.11); withdrawals need a reason. */
export function ContributionForm({ goal, open, onClose }: ContributionFormProps) {
  const add = useAddContribution()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ContributionFormValues>({
    resolver: zodResolver(contributionFormSchema),
    defaultValues: EMPTY_CONTRIBUTION_FORM,
  })

  useEffect(() => {
    if (open) reset({ ...EMPTY_CONTRIBUTION_FORM, contributedOn: toIsoDate(new Date()) })
  }, [open, reset])

  const onSubmit = handleSubmit((values) => {
    add.mutate(
      { goalId: goal.id, input: toCreateContributionInput(values, goal) },
      {
        onSuccess: () => {
          const withdrawal =
            (parseAmountToMinor(values.amount, fractionDigitsFor(goal.currencyCode)) ?? 0) < 0
          toast.push({ tone: 'success', message: withdrawal ? 'Withdrawal recorded' : 'Contribution recorded' })
          onClose()
        },
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: cause instanceof ApiError ? cause.message : 'Could not record the contribution',
          }),
      },
    )
  })

  return (
    <Modal
      open={open}
      title="Add contribution"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="contribution-form" loading={add.isPending}>
            Record
          </Button>
        </>
      }
    >
      <form id="contribution-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Amount"
            placeholder="0.00"
            numeric
            inputMode="decimal"
            hint={`In ${goal.currencyCode} (negative withdraws)`}
            error={errors.amount?.message}
            {...register('amount')}
          />
          <Input
            label="Date"
            type="date"
            error={errors.contributedOn?.message}
            {...register('contributedOn')}
          />
        </div>

        <Select
          label="Method"
          hint={
            goal.linkedAccountId === null
              ? 'TRANSFER needs a linked account on the goal'
              : 'TRANSFER moves money between accounts'
          }
          error={errors.method?.message}
          {...register('method')}
        >
          {(['MANUAL', 'TRANSFER', 'ADJUSTMENT'] as const).map((option) => (
            <option key={option} value={option} disabled={option === 'TRANSFER' && goal.linkedAccountId === null}>
              {GOAL_CONTRIBUTION_METHOD_LABELS[option]}
            </option>
          ))}
        </Select>

        <Input
          label="Notes"
          placeholder="Reason for withdrawal or a note"
          error={errors.notes?.message}
          autoComplete="off"
          {...register('notes')}
        />
      </form>
    </Modal>
  )
}