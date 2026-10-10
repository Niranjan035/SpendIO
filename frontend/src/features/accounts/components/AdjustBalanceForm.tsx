import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { formatMinor, fractionDigitsFor } from '@/lib/money'
import { adjustBalanceSchema, toAdjustBalanceInput, type AdjustBalanceFormValues } from '../schemas'
import { useAdjustBalance } from '../hooks'
import type { Account, AccountBalance } from '../types'

export interface AdjustBalanceFormProps {
  account: Account
  balance: AccountBalance | undefined
}

export function AdjustBalanceForm({ account, balance }: AdjustBalanceFormProps) {
  const adjust = useAdjustBalance()
  const toast = useToast()
  const digits = fractionDigitsFor(account.currencyCode)

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<AdjustBalanceFormValues>({
    resolver: zodResolver(adjustBalanceSchema),
    defaultValues: {
      newBalance: balance ? formatMinor(balance.balanceMinor, digits) : '',
      occurredOn: new Date().toISOString().slice(0, 10),
      reason: '',
    },
  })

  const onSubmit = handleSubmit((values) =>
    adjust.mutate(
      { id: account.id, input: toAdjustBalanceInput(values, account.currencyCode) },
      {
        onSuccess: () => toast.push({ tone: 'success', message: 'Balance adjusted' }),
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: cause instanceof ApiError ? cause.message : 'Could not adjust the balance',
          }),
      },
    ),
  )

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Input
          label="New balance"
          placeholder="0.00"
          numeric
          hint={`Leave a note so the adjustment makes sense later`}
          error={errors.newBalance?.message}
          {...register('newBalance')}
        />
        <Input
          label="Effective date"
          type="date"
          max={new Date().toISOString().slice(0, 10)}
          error={errors.occurredOn?.message}
          {...register('occurredOn')}
        />
      </div>
      <Input
        label="Reason"
        placeholder="Why are you adjusting the balance?"
        error={errors.reason?.message}
        autoComplete="off"
        {...register('reason')}
      />
      <div className="flex justify-end">
        <Button type="submit" variant="secondary" loading={adjust.isPending} disabled={!isDirty}>
          Adjust balance
        </Button>
      </div>
    </form>
  )
}
