import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { baseCurrencySchema, type BaseCurrencyFormValues } from '../schemas'
import { useChangeBaseCurrency, useCurrencies, useProfile } from '../hooks'

export function BaseCurrencyForm() {
  const { data: profile } = useProfile()
  const { data: currencies, isLoading, error } = useCurrencies()
  const change = useChangeBaseCurrency()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<BaseCurrencyFormValues>({
    resolver: zodResolver(baseCurrencySchema),
    defaultValues: { currencyCode: '' },
  })

  useEffect(() => {
    if (profile) reset({ currencyCode: profile.baseCurrencyCode })
  }, [profile, reset])

  const onSubmit = handleSubmit((values) =>
    change.mutate(
      { currencyCode: values.currencyCode, revalueExistingTransactions: false },
      {
        onSuccess: (updated) =>
          toast.push({
            tone: 'success',
            message: `Base currency is now ${updated.baseCurrencyCode}. Past transactions keep the rates they were recorded at.`,
          }),
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message:
              cause instanceof ApiError ? cause.message : 'Could not change the base currency',
          }),
      },
    ),
  )

  if (isLoading) return <LoadingSpinner label="Loading currencies" />

  if (error || !currencies) {
    return (
      <p role="alert" className="text-sm text-danger">
        {error instanceof ApiError ? error.message : 'Could not load currencies.'}
      </p>
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <Select
        label="Base currency"
        hint="Amounts are converted into this currency for balances and reports."
        error={errors.currencyCode?.message}
        {...register('currencyCode')}
      >
        {currencies.map((currency) => (
          <option key={currency.code} value={currency.code}>
            {currency.code} — {currency.name}
            {currency.symbol ? ` (${currency.symbol})` : ''}
          </option>
        ))}
      </Select>

      <div className="flex justify-end">
        <Button type="submit" loading={change.isPending}>
          Change base currency
        </Button>
      </div>
    </form>
  )
}
