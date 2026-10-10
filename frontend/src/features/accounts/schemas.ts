import { z } from 'zod'
import { fractionDigitsFor, parseAmountToMinor } from '@/lib/money'
import { ACCOUNT_TYPES } from '@/types/enums'
import type { AccountType } from '@/types/enums'
import type { AdjustBalanceInput, CreateAccountInput, UpdateAccountInput } from './types'

const CURRENCY_PATTERN = /^[A-Za-z]{3}$/
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const LAST4_PATTERN = /^\d{4}$/
const COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/

const accountTypeEnum = z.enum(ACCOUNT_TYPES as unknown as [AccountType, ...AccountType[]])

export const accountFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(80, 'Max 80 characters'),
    type: accountTypeEnum,
    currencyCode: z
      .string()
      .trim()
      .regex(CURRENCY_PATTERN, 'Enter a 3-letter ISO code')
      .transform((value) => value.toUpperCase()),
    openingBalance: z
      .string()
      .refine((value) => parseAmountToMinor(value) !== null, 'Enter a valid amount'),
    openingBalanceDate: z
      .string()
      .refine((value) => value === '' || DATE_PATTERN.test(value), 'Pick a date'),
    openingTransactionDate: z
      .string()
      .refine((value) => value === '' || DATE_PATTERN.test(value), 'Pick a date'),
    createOpeningBalanceTransaction: z.boolean(),
    primary: z.boolean(),
    institution: z.string().trim().max(80, 'Max 80 characters'),
    last4: z
      .string()
      .trim()
      .refine((value) => value === '' || LAST4_PATTERN.test(value), 'Exactly 4 digits'),
    creditLimit: z
      .string()
      .refine(
        (value) => value === '' || parseAmountToMinor(value) !== null,
        'Enter a valid amount',
      ),
    color: z
      .string()
      .trim()
      .refine((value) => value === '' || COLOR_PATTERN.test(value), 'Use hex like #12AB34'),
    notes: z.string().trim().max(500, 'Max 500 characters'),
  })
  .refine(
    (values) => {
      const amount = parseAmountToMinor(values.openingBalance) ?? 0
      return (
        !values.createOpeningBalanceTransaction || amount === 0 || values.openingBalanceDate !== ''
      )
    },
    { path: ['openingBalanceDate'], message: 'Pick an opening date' },
  )

export const editAccountSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(80, 'Max 80 characters'),
  institution: z.string().trim().max(80, 'Max 80 characters'),
  last4: z
    .string()
    .trim()
    .refine((value) => value === '' || LAST4_PATTERN.test(value), 'Exactly 4 digits'),
  color: z
    .string()
    .trim()
    .refine((value) => value === '' || COLOR_PATTERN.test(value), 'Use hex like #12AB34'),
  notes: z.string().trim().max(500, 'Max 500 characters'),
})

export const adjustBalanceSchema = z.object({
  newBalance: z
    .string()
    .refine((value) => parseAmountToMinor(value) !== null, 'Enter a valid amount'),
  occurredOn: z.string().regex(DATE_PATTERN, 'Pick a date'),
  reason: z.string().trim().min(1, 'Reason is required').max(255, 'Max 255 characters'),
})

export type AccountFormValues = z.infer<typeof accountFormSchema>
export type EditAccountFormValues = z.infer<typeof editAccountSchema>
export type AdjustBalanceFormValues = z.infer<typeof adjustBalanceSchema>

export const EMPTY_ACCOUNT_FORM: AccountFormValues = {
  name: '',
  type: 'BANK',
  currencyCode: '',
  openingBalance: '',
  openingBalanceDate: '',
  openingTransactionDate: '',
  createOpeningBalanceTransaction: true,
  primary: false,
  institution: '',
  last4: '',
  creditLimit: '',
  color: '',
  notes: '',
}

/**
 * Optional fields on the wire are nullable, not empty strings: the backend validates
 * {@code last4}/{@code color} with an anchored {@code @Pattern}, which an empty string
 * fails (null passes). Send null for anything left blank.
 */
function blankToNull(value: string): string | null {
  return value.trim() === '' ? null : value
}

export function toCreateAccountInput(values: AccountFormValues): CreateAccountInput {
  const digits = fractionDigitsFor(values.currencyCode)
  const amount = parseAmountToMinor(values.openingBalance, digits) ?? 0
  const wantsOpeningTx =
    values.createOpeningBalanceTransaction && amount !== 0 && values.openingTransactionDate !== ''
  const openingDate = values.createOpeningBalanceTransaction ? values.openingBalanceDate : ''
  return {
    name: values.name,
    type: values.type,
    currencyCode: values.currencyCode,
    openingBalanceMinor: amount,
    openingBalanceDate: openingDate === '' ? null : openingDate,
    institution: blankToNull(values.institution),
    last4: blankToNull(values.last4),
    creditLimitMinor: values.creditLimit ? parseAmountToMinor(values.creditLimit, digits) : null,
    billingDay: null,
    dueDay: null,
    icon: null,
    color: blankToNull(values.color),
    notes: blankToNull(values.notes),
    primary: values.primary,
    openingTransactionDate: wantsOpeningTx ? values.openingTransactionDate : null,
    createOpeningBalanceTransaction: wantsOpeningTx,
  }
}

export function toAdjustBalanceInput(
  values: AdjustBalanceFormValues,
  currencyCode: string,
): AdjustBalanceInput {
  const digits = fractionDigitsFor(currencyCode)
  const parsed = parseAmountToMinor(values.newBalance, digits)
  if (parsed === null) throw new TypeError(`invalid new balance: ${values.newBalance}`)
  return { newBalanceMinor: parsed, occurredOn: values.occurredOn, reason: values.reason }
}

export function toUpdateAccountInput(values: EditAccountFormValues): UpdateAccountInput {
  return {
    name: values.name,
    institution: blankToNull(values.institution),
    last4: blankToNull(values.last4),
    color: blankToNull(values.color),
    notes: blankToNull(values.notes),
  }
}
