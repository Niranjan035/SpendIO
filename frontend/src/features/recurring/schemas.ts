import { z } from 'zod'
import { toIsoDate } from '@/lib/dates'
import { formatMinor, fractionDigitsFor, parseAmountToMinor } from '@/lib/money'
import type { CreateRecurringInput, RecurringTransaction, UpdateRecurringInput } from './types'

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export const recurringFormSchema = z
  .object({
    accountId: z.string().regex(/^\d+$/, 'Pick an account'),
    type: z.enum(['INCOME', 'EXPENSE', 'TRANSFER']),
    toAccountId: z.string(),
    categoryId: z.string(),
    amount: z
      .string()
      .refine((value) => (parseAmountToMinor(value) ?? 0) > 0, 'Enter a positive amount'),
    description: z.string().trim().max(255, 'Max 255 characters'),
    notes: z.string().trim().max(16383, 'Max 16383 characters'),
    frequency: z.enum(['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'CUSTOM']),
    intervalCount: z
      .string()
      .regex(/^\d+$/, 'Enter how often')
      .refine((value) => Number(value) >= 1, 'At least 1'),
    dayOfMonth: z.string().regex(/^$|^([1-9]|[12]\d|3[01])$/, '1–31'),
    dayOfWeek: z.string().regex(/^$|^[1-7]$/, '1–7'),
    skipWeekends: z.boolean(),
    startDate: z.string().regex(DATE_PATTERN, 'Pick a date'),
    endDate: z.string().regex(/^$|^\d{4}-\d{2}-\d{2}$/, 'Pick a date'),
    autoPost: z.boolean(),
    maxRetries: z
      .string()
      .regex(/^\d+$/, 'Enter a number')
      .refine((value) => Number(value) >= 1 && Number(value) <= 99, '1–99'),
    targetOccurrenceDate: z.string().regex(/^$|^\d{4}-\d{2}-\d{2}$/, 'Pick a date'),
  })
  .superRefine((values, ctx) => {
    const transfer = values.type === 'TRANSFER'
    if (transfer && values.toAccountId === '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['toAccountId'],
        message: 'Pick a destination account',
      })
    }
    if (transfer && values.categoryId !== '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['categoryId'],
        message: 'Transfer rules cannot have a category',
      })
    }
    if (
      transfer &&
      values.toAccountId !== '' &&
      values.accountId !== '' &&
      values.toAccountId === values.accountId
    ) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['toAccountId'],
        message: 'Pick a different destination account',
      })
    }
    if (!transfer && values.categoryId === '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['categoryId'],
        message: 'Pick a category',
      })
    }
    if (!transfer && values.toAccountId !== '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['toAccountId'],
        message: 'Only transfers have a destination account',
      })
    }

    const frequency = values.frequency
    if (frequency === 'MONTHLY' && values.dayOfMonth === '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['dayOfMonth'],
        message: 'Pick a day of the month',
      })
    }
    if (frequency === 'WEEKLY') {
      if (values.dayOfWeek === '') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dayOfWeek'],
          message: 'Pick a day of the week',
        })
      }
      if (values.dayOfMonth !== '') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dayOfMonth'],
          message: 'Weekly rules ignore the day of the month',
        })
      }
    }
    if (frequency === 'DAILY' || frequency === 'YEARLY') {
      if (values.dayOfMonth !== '') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dayOfMonth'],
          message: `${frequency === 'DAILY' ? 'Daily' : 'Yearly'} rules ignore the day of the month`,
        })
      }
      if (values.dayOfWeek !== '') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dayOfWeek'],
          message: `${frequency === 'DAILY' ? 'Daily' : 'Yearly'} rules ignore the day of the week`,
        })
      }
    }
    if (frequency === 'CUSTOM') {
      if (values.targetOccurrenceDate === '') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['targetOccurrenceDate'],
          message: 'Pick the first payday',
        })
      }
      if (values.dayOfMonth !== '') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dayOfMonth'],
          message: 'Custom rules ignore the day of the month',
        })
      }
      if (values.dayOfWeek !== '') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['dayOfWeek'],
          message: 'Custom rules ignore the day of the week',
        })
      }
    }

    if (values.endDate !== '' && values.endDate < values.startDate) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['endDate'],
        message: 'End date must be on or after the start date',
      })
    }
  })

export type RecurringFormValues = z.infer<typeof recurringFormSchema>

export const EMPTY_RECURRING_FORM: RecurringFormValues = {
  accountId: '',
  type: 'EXPENSE',
  toAccountId: '',
  categoryId: '',
  amount: '',
  description: '',
  notes: '',
  frequency: 'MONTHLY',
  intervalCount: '1',
  dayOfMonth: String(new Date().getDate()),
  dayOfWeek: '1',
  skipWeekends: false,
  startDate: toIsoDate(new Date()),
  endDate: '',
  autoPost: true,
  maxRetries: '3',
  targetOccurrenceDate: '',
}

/** Defaults the schedule day fields the chosen frequency needs when the rule has none. */
export function recurringFormDefaults(rule: RecurringTransaction): RecurringFormValues {
  const startDate = rule.startDate.slice(0, 10)
  return {
    accountId: String(rule.account.id),
    type: rule.type === 'INCOME' || rule.type === 'EXPENSE' || rule.type === 'TRANSFER' ? rule.type : 'EXPENSE',
    toAccountId: rule.toAccount ? String(rule.toAccount.id) : '',
    categoryId: rule.category ? String(rule.category.id) : '',
    amount: formatMinor(rule.amountMinor, fractionDigitsFor(rule.currencyCode)),
    description: rule.description ?? '',
    notes: rule.notes ?? '',
    frequency: rule.frequency,
    intervalCount: String(Math.max(1, rule.intervalCount)),
    dayOfMonth:
      rule.dayOfMonth !== null ? String(rule.dayOfMonth) : String(Number(startDate.slice(8, 10))),
    dayOfWeek:
      rule.dayOfWeek !== null
        ? String(rule.dayOfWeek)
        : String(startOfWeekDay(new Date(`${startDate}T00:00:00`))),
    skipWeekends: rule.skipWeekends,
    startDate: rule.startDate,
    endDate: rule.endDate ?? '',
    autoPost: rule.autoPost,
    maxRetries: String(Math.max(1, Math.min(99, rule.maxRetries))),
    targetOccurrenceDate: rule.targetOccurrenceDate ?? '',
  }
}

function startOfWeekDay(date: Date): number {
  const day = date.getDay()
  return day === 0 ? 7 : day
}

export function toCreateRecurringInput(
  values: RecurringFormValues,
  account: Pick<{ id: number; currencyCode: string }, 'currencyCode'>,
): CreateRecurringInput {
  const amountMinor = parseAmountToMinor(values.amount, fractionDigitsFor(account.currencyCode))
  if (amountMinor === null || amountMinor <= 0) {
    throw new TypeError(`invalid amount: ${values.amount}`)
  }
  const transfer = values.type === 'TRANSFER'
  return {
    accountId: Number(values.accountId),
    toAccountId: transfer ? Number(values.toAccountId) : null,
    categoryId: transfer ? null : Number(values.categoryId),
    type: values.type,
    amountMinor,
    currencyCode: account.currencyCode,
    description: values.description === '' ? null : values.description,
    notes: values.notes === '' ? null : values.notes,
    frequency: values.frequency,
    intervalCount: Number(values.intervalCount),
    dayOfMonth: values.dayOfMonth === '' ? null : Number(values.dayOfMonth),
    dayOfWeek: values.dayOfWeek === '' ? null : Number(values.dayOfWeek),
    skipWeekends: values.skipWeekends,
    startDate: values.startDate,
    endDate: values.endDate === '' ? null : values.endDate,
    autoPost: values.autoPost,
    maxRetries: Number(values.maxRetries),
    targetOccurrenceDate: values.targetOccurrenceDate === '' ? null : values.targetOccurrenceDate,
  }
}

export function toUpdateRecurringInput(
  values: RecurringFormValues,
  account: Pick<{ id: number; currencyCode: string }, 'currencyCode'>,
  version: number,
): UpdateRecurringInput {
  const { ...input } = toCreateRecurringInput(values, account)
  return { ...input, version, description: values.description, notes: values.notes }
}