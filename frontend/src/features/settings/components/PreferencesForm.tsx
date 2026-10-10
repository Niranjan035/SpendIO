import { useEffect, type InputHTMLAttributes } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import {
  DATE_FORMAT_OPTIONS,
  FIRST_DAY_OPTIONS,
  preferencesSchema,
  type PreferencesFormValues,
} from '../schemas'
import { usePreferences, useSavePreferences } from '../hooks'

const THEME_OPTIONS = [
  { value: 'SYSTEM', label: 'Match system' },
  { value: 'LIGHT', label: 'Light' },
  { value: 'DARK', label: 'Dark' },
] as const

const DECIMAL_OPTIONS = [
  { value: 'DOT', label: 'Dot — 1,234.56' },
  { value: 'COMMA', label: 'Comma — 1.234,56' },
] as const

const POSITION_OPTIONS = [
  { value: 'BEFORE', label: 'Before the amount — €12.00' },
  { value: 'AFTER', label: 'After the amount — 12.00€' },
] as const

function Toggle({
  label,
  hint,
  error,
  ...rest
}: { label: string; hint?: string; error?: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="flex items-start gap-3 rounded-md border border-border bg-surface p-3">
      <input type="checkbox" className="mt-0.5 h-4 w-4 shrink-0 accent-primary" {...rest} />
      <span className="min-w-0">
        <span className="block text-sm font-medium text-text">{label}</span>
        {hint && <span className="mt-0.5 block text-xs text-muted">{hint}</span>}
        {error && (
          <span role="alert" className="mt-0.5 block text-xs text-danger">
            {error}
          </span>
        )}
      </span>
    </label>
  )
}

export function PreferencesForm() {
  const { data: preferences, isLoading, error } = usePreferences()
  const save = useSavePreferences()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<PreferencesFormValues>({
    resolver: zodResolver(preferencesSchema),
    defaultValues: preferences ?? undefined,
  })

  useEffect(() => {
    if (preferences) reset(preferences)
  }, [preferences, reset])

  const onSubmit = handleSubmit((values) =>
    save.mutate(values, {
      onSuccess: () => toast.push({ tone: 'success', message: 'Preferences saved' }),
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'Could not save preferences',
        }),
    }),
  )

  if (isLoading) return <LoadingSpinner label="Loading preferences" />

  if (error || !preferences) {
    return (
      <p role="alert" className="text-sm text-danger">
        {error instanceof ApiError ? error.message : 'Could not load your preferences.'}
      </p>
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
      <section className="flex flex-col gap-4">
        <h3 className="text-sm font-semibold text-text">Display</h3>
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Theme" error={errors.theme?.message} {...register('theme')}>
            {THEME_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
          <Select
            label="Date format"
            error={errors.dateFormat?.message}
            {...register('dateFormat')}
          >
            {DATE_FORMAT_OPTIONS.map((format) => (
              <option key={format} value={format}>
                {format}
              </option>
            ))}
          </Select>
          <Select
            label="Week starts on"
            error={errors.firstDayOfWeek?.message}
            {...register('firstDayOfWeek', { valueAsNumber: true })}
          >
            {FIRST_DAY_OPTIONS.map((day) => (
              <option key={day.value} value={day.value}>
                {day.label}
              </option>
            ))}
          </Select>
          <Select
            label="Decimal separator"
            error={errors.decimalStyle?.message}
            {...register('decimalStyle')}
          >
            {DECIMAL_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
          <Input
            label="Group separator"
            hint="Shown between thousands"
            maxLength={4}
            error={errors.groupSeparator?.message}
            {...register('groupSeparator')}
          />
          <Input
            label="Number format"
            hint="Pattern such as #,##0.00"
            maxLength={24}
            error={errors.numberFormat?.message}
            {...register('numberFormat')}
          />
          <Select
            label="Currency symbol"
            error={errors.currencySymbolPosition?.message}
            {...register('currencySymbolPosition')}
          >
            {POSITION_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>
      </section>

      <section className="flex flex-col gap-4">
        <h3 className="text-sm font-semibold text-text">Alerts & reminders</h3>
        <div className="grid gap-4 sm:grid-cols-3">
          <Input
            label="Budget alert at (%)"
            type="number"
            min={1}
            max={100}
            numeric
            error={errors.budgetAlertThresholdPct?.message}
            {...register('budgetAlertThresholdPct', { valueAsNumber: true })}
          />
          <Input
            label="Goal reminder (days)"
            type="number"
            min={0}
            max={365}
            numeric
            error={errors.goalReminderDays?.message}
            {...register('goalReminderDays', { valueAsNumber: true })}
          />
          <Input
            label="Recurring reminder (days)"
            type="number"
            min={0}
            max={30}
            numeric
            error={errors.recurringReminderDays?.message}
            {...register('recurringReminderDays', { valueAsNumber: true })}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Toggle
            label="Weekly email digest"
            hint="A summary of balances and upcoming payments."
            error={errors.emailDigestEnabled?.message}
            {...register('emailDigestEnabled')}
          />
          <Toggle
            label="Reduce motion"
            hint="Calms charts and animated transitions."
            error={errors.reducedMotion?.message}
            {...register('reducedMotion')}
          />
        </div>
      </section>

      <div className="flex justify-end">
        <Button type="submit" loading={save.isPending} disabled={!isDirty}>
          Save preferences
        </Button>
      </div>
    </form>
  )
}
