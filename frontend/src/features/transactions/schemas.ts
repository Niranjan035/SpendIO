import { z } from 'zod'
import { toIsoDate } from '@/lib/dates'
import { fractionDigitsFor, parseAmountToMinor } from '@/lib/money'
import type {
  AccountOption,
  CreateTransactionInput,
  CreateTransferInput,
  UpdateTransactionInput,
} from './types'

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export const transactionFormSchema = z
  .object({
    accountId: z.string().regex(/^\d+$/, 'Pick an account'),
    type: z.enum(['INCOME', 'EXPENSE', 'ADJUSTMENT']),
    direction: z.enum(['INFLOW', 'OUTFLOW']),
    amount: z
      .string()
      .refine((value) => (parseAmountToMinor(value) ?? 0) > 0, 'Enter a positive amount'),
    occurredOn: z.string().regex(DATE_PATTERN, 'Pick a date'),
    categoryId: z.string(),
    description: z.string().trim().max(255, 'Max 255 characters'),
    merchant: z.string().trim().max(160, 'Max 160 characters'),
    payee: z.string().trim().max(160, 'Max 160 characters'),
    notes: z.string().trim().max(16383, 'Max 16383 characters'),
    status: z.enum(['POSTED', 'PENDING', 'VOID']),
  })
  .superRefine((values, ctx) => {
    if (values.type !== 'ADJUSTMENT' && values.categoryId === '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['categoryId'],
        message: 'Pick a category',
      })
    }
    if (values.type === 'ADJUSTMENT' && values.categoryId !== '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['categoryId'],
        message: 'Adjustments cannot have a category',
      })
    }
    if (values.type !== 'ADJUSTMENT' && values.status === 'VOID') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['status'],
        message: 'Only adjustments can be voided',
      })
    }
  })

export type TransactionFormValues = z.infer<typeof transactionFormSchema>

export const EMPTY_TRANSACTION_FORM: TransactionFormValues = {
  accountId: '',
  type: 'EXPENSE',
  direction: 'OUTFLOW',
  amount: '',
  occurredOn: toIsoDate(new Date()),
  categoryId: '',
  description: '',
  merchant: '',
  payee: '',
  notes: '',
  status: 'POSTED',
}

export function toCreateTransactionInput(
  values: TransactionFormValues,
  account: Pick<AccountOption, 'currencyCode'>,
): CreateTransactionInput {
  const amountMinor = parseAmountToMinor(values.amount, fractionDigitsFor(account.currencyCode))
  if (amountMinor === null || amountMinor <= 0) {
    throw new TypeError(`invalid amount: ${values.amount}`)
  }
  const adjustment = values.type === 'ADJUSTMENT'
  return {
    accountId: Number(values.accountId),
    categoryId: values.categoryId === '' ? null : Number(values.categoryId),
    type: values.type,
    direction: adjustment ? values.direction : null,
    amountMinor,
    currencyCode: account.currencyCode,
    exchangeRate: null,
    occurredOn: values.occurredOn,
    description: values.description === '' ? null : values.description,
    merchant: values.merchant === '' ? null : values.merchant,
    payee: values.payee === '' ? null : values.payee,
    notes: values.notes === '' ? null : values.notes,
    status: adjustment ? values.status : 'POSTED',
    dedupeKey: null,
  }
}

export function toUpdateTransactionInput(
  values: TransactionFormValues,
  currencyCode: string,
  version: number,
): UpdateTransactionInput {
  const amountMinor = parseAmountToMinor(values.amount, fractionDigitsFor(currencyCode))
  if (amountMinor === null || amountMinor <= 0) {
    throw new TypeError(`invalid amount: ${values.amount}`)
  }
  return {
    accountId: Number(values.accountId),
    categoryId: values.categoryId === '' ? null : Number(values.categoryId),
    amountMinor,
    currencyCode,
    occurredOn: values.occurredOn,
    description: values.description,
    notes: values.notes,
    version,
  }
}

export const transferFormSchema = z
  .object({
    fromAccountId: z.string().regex(/^\d+$/, 'Pick an account'),
    toAccountId: z.string().regex(/^\d+$/, 'Pick an account'),
    amount: z
      .string()
      .refine((value) => (parseAmountToMinor(value) ?? 0) > 0, 'Enter a positive amount'),
    occurredOn: z.string().regex(DATE_PATTERN, 'Pick a date'),
    description: z.string().trim().max(255, 'Max 255 characters'),
    notes: z.string().trim().max(16383, 'Max 16383 characters'),
  })
  .superRefine((values, ctx) => {
    if (values.fromAccountId !== '' && values.fromAccountId === values.toAccountId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['toAccountId'],
        message: 'Pick a different destination account',
      })
    }
  })

export type TransferFormValues = z.infer<typeof transferFormSchema>

export const EMPTY_TRANSFER_FORM: TransferFormValues = {
  fromAccountId: '',
  toAccountId: '',
  amount: '',
  occurredOn: toIsoDate(new Date()),
  description: '',
  notes: '',
}

/** The transfer amount is always stated in the source account's currency. */
export function toCreateTransferInput(
  values: TransferFormValues,
  source: Pick<AccountOption, 'currencyCode'>,
): CreateTransferInput {
  const amountMinor = parseAmountToMinor(values.amount, fractionDigitsFor(source.currencyCode))
  if (amountMinor === null || amountMinor <= 0) {
    throw new TypeError(`invalid amount: ${values.amount}`)
  }
  return {
    fromAccountId: Number(values.fromAccountId),
    toAccountId: Number(values.toAccountId),
    amountMinor,
    currencyCode: source.currencyCode,
    toAmountMinor: null,
    occurredOn: values.occurredOn,
    description: values.description === '' ? null : values.description,
    notes: values.notes === '' ? null : values.notes,
  }
}

export const reverseFormSchema = z.object({
  reason: z.string().trim().min(1, 'Enter a reason').max(255, 'Max 255 characters'),
  occurredOn: z.string().regex(DATE_PATTERN, 'Pick a date'),
})

export type ReverseFormValues = z.infer<typeof reverseFormSchema>

export const EMPTY_REVERSE_FORM: ReverseFormValues = {
  reason: '',
  occurredOn: toIsoDate(new Date()),
}
