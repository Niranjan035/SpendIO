import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { formatMinor, fractionDigitsFor } from '@/lib/money'
import { ACCOUNT_TYPES } from '@/types/enums'
import { ACCOUNT_TYPE_LABELS } from '../labels'
import {
  accountFormSchema,
  editAccountSchema,
  EMPTY_ACCOUNT_FORM,
  toCreateAccountInput,
  toUpdateAccountInput,
  type AccountFormValues,
} from '../schemas'
import type { Resolver } from 'react-hook-form'
import { useCreateAccount, useUpdateAccount } from '../hooks'
import type { Account } from '../types'

export interface AccountFormModalProps {
  account: Account | null
  open: boolean
  onClose: () => void
}

export function AccountFormModal({ account, open, onClose }: AccountFormModalProps) {
  const creating = account === null
  const create = useCreateAccount()
  const update = useUpdateAccount()
  const toast = useToast()

  /**
   * Edit mode validates only the editable fields. The create schema requires currency,
   * opening balance and friends; in edit mode those live in the defaults but are not
   * rendered, so validating them would silently block the submit on hidden field errors.
   */
  const resolver: Resolver<AccountFormValues> = creating
    ? zodResolver(accountFormSchema)
    : (zodResolver(editAccountSchema) as unknown as Resolver<AccountFormValues>)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    formState: { errors },
  } = useForm<AccountFormValues>({
    resolver,
    defaultValues: EMPTY_ACCOUNT_FORM,
  })

  const accountType = useWatch({ control, name: 'type' })
  const wantOpeningTx = useWatch({ control, name: 'createOpeningBalanceTransaction' })
  const isCreditCard = accountType === 'CREDIT_CARD'

  /** Backend field names (CreateAccountRequest) mapped onto the form's field names. */
  const serverFieldAliases: Partial<Record<string, keyof AccountFormValues>> = {
    name: 'name',
    type: 'type',
    currencyCode: 'currencyCode',
    openingBalanceMinor: 'openingBalance',
    openingBalanceDate: 'openingBalanceDate',
    openingTransactionDate: 'openingTransactionDate',
    institution: 'institution',
    last4: 'last4',
    creditLimitMinor: 'creditLimit',
    color: 'color',
    notes: 'notes',
  }

  /** Only map onto inputs the current mode actually renders, so nothing is lost. */
  const fieldIsVisible = (field: keyof AccountFormValues): boolean => {
    if (field === 'last4' || field === 'creditLimit') return isCreditCard
    if (field === 'color') return !isCreditCard
    if (creating) return true
    return field === 'name' || field === 'institution' || field === 'notes'
  }

  const reportError = (cause: unknown, fallback: string): void => {
    if (cause instanceof ApiError && cause.fieldErrors.length > 0) {
      const unmapped: string[] = []
      for (const fieldError of cause.fieldErrors) {
        const formField = serverFieldAliases[fieldError.field]
        if (formField && fieldIsVisible(formField)) {
          setError(formField, { type: 'server', message: fieldError.message })
        } else {
          unmapped.push(fieldError.message)
        }
      }
      if (unmapped.length > 0) {
        toast.push({ tone: 'error', message: unmapped.join('; ') })
      }
      return
    }
    toast.push({
      tone: 'error',
      message: cause instanceof ApiError ? cause.message : fallback,
    })
  }

  useEffect(() => {
    if (!open) return
    if (creating) {
      reset(EMPTY_ACCOUNT_FORM)
    } else {
      reset({
        ...EMPTY_ACCOUNT_FORM,
        type: account.type,
        name: account.name,
        institution: account.institution ?? '',
        last4: account.last4 ?? '',
        color: account.color ?? '',
        notes: account.notes ?? '',
      })
    }
  }, [open, creating, account, reset])

  const onSubmit = handleSubmit((values) => {
    if (creating) {
      create.mutate(toCreateAccountInput(values), {
        onSuccess: () => {
          toast.push({ tone: 'success', message: 'Account created' })
          onClose()
        },
        onError: (cause) => reportError(cause, 'Could not create the account'),
      })
    } else {
      update.mutate(
        { id: account.id, input: toUpdateAccountInput(values) },
        {
          onSuccess: () => {
            toast.push({ tone: 'success', message: 'Account updated' })
            onClose()
          },
          onError: (cause) => reportError(cause, 'Could not update the account'),
        },
      )
    }
  })

  return (
    <Modal
      open={open}
      title={creating ? 'New account' : 'Edit account'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="account-form" loading={create.isPending || update.isPending}>
            {creating ? 'Create account' : 'Save changes'}
          </Button>
        </>
      }
    >
      <form id="account-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Name"
            placeholder="Everyday checking"
            error={errors.name?.message}
            autoComplete="off"
            {...register('name')}
          />
          <Select
            label="Type"
            error={errors.type?.message}
            disabled={!creating}
            hint={creating ? undefined : 'Read-only after creation'}
            {...register('type')}
          >
            {ACCOUNT_TYPES.map((type) => (
              <option key={type} value={type}>
                {ACCOUNT_TYPE_LABELS[type]}
              </option>
            ))}
          </Select>
        </div>

        {creating && (
          <>
            <Input
              label="Currency"
              placeholder="USD"
              hint="ISO 4217 code, e.g. USD or EUR"
              error={errors.currencyCode?.message}
              autoComplete="off"
              {...register('currencyCode')}
            />

            <label className="flex items-center gap-2 text-sm font-medium text-text">
              <input
                type="checkbox"
                className="h-4 w-4"
                {...register('createOpeningBalanceTransaction')}
              />
              Record an existing opening balance
            </label>

            {wantOpeningTx && (
              <div className="grid gap-4 sm:grid-cols-2">
                <Input
                  label="Opening amount"
                  placeholder="0.00"
                  numeric
                  hint="Positive for money you hold"
                  error={errors.openingBalance?.message}
                  {...register('openingBalance')}
                />
                <Input
                  label="Opening date"
                  type="date"
                  max={new Date().toISOString().slice(0, 10)}
                  error={errors.openingBalanceDate?.message}
                  {...register('openingBalanceDate')}
                />
              </div>
            )}

            {isCreditCard && (
              <Input
                label="Credit limit"
                placeholder="0.00"
                numeric
                hint="Used to show available credit"
                error={errors.creditLimit?.message}
                {...register('creditLimit')}
              />
            )}

            <label className="flex items-center gap-2 text-sm font-medium text-text">
              <input type="checkbox" className="h-4 w-4" {...register('primary')} />
              Make this the primary account
            </label>
          </>
        )}

        {!creating && (
          <dl className="grid gap-3 rounded-md border border-border bg-surface-sunken p-4 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-xs text-muted">Currency</dt>
              <dd className="mt-0.5 text-text">{account.currencyCode}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Type</dt>
              <dd className="mt-0.5 text-text">{ACCOUNT_TYPE_LABELS[account.type]}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Opening balance</dt>
              <dd className="mt-0.5 text-text num">
                {formatMinor(account.openingBalanceMinor, fractionDigitsFor(account.currencyCode))}
              </dd>
            </div>
          </dl>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Institution"
            placeholder="Optional bank or issuer"
            error={errors.institution?.message}
            autoComplete="off"
            {...register('institution')}
          />
          {isCreditCard ? (
            <Input
              label="Last 4 digits"
              placeholder="1234"
              hint="Identifier, not a secret"
              error={errors.last4?.message}
              inputMode="numeric"
              maxLength={4}
              {...register('last4')}
            />
          ) : (
            <Input
              label="Colour"
              placeholder="#12AB34"
              hint="Optional accent for this account"
              error={errors.color?.message}
              {...register('color')}
            />
          )}
        </div>

        <Input
          label="Notes"
          placeholder="Optional notes about this account"
          error={errors.notes?.message}
          autoComplete="off"
          {...register('notes')}
        />

        <p className="text-xs text-muted">
          {creating
            ? 'Currency and the opening balance are fixed once the account has transactions.'
            : 'Currency and type are shown for reference and cannot be changed here.'}
        </p>
      </form>
    </Modal>
  )
}
