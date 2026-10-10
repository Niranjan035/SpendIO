import { addMonths, endOfMonth, endOfQuarter, endOfWeek, endOfYear, startOfMonth, startOfQuarter, startOfWeek, startOfYear } from 'date-fns'
import { z } from 'zod'
import { toIsoDate } from '@/lib/dates'
import { formatMinor, fractionDigitsFor, parseAmountToMinor } from '@/lib/money'
import type { BudgetPeriod, BudgetStatus } from '@/types/enums'
import type { Budget, CreateBudgetInput, UpdateBudgetInput } from './types'

const MAX_CUSTOM_DAYS = 731

export const BUDGET_PERIOD_VALUES = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'CUSTOM'] as const

/** The window a {@code periodType} fixes to (backend D.10 canonical-boundary rules). */
export function canonicalPeriod(
  periodType: BudgetPeriod,
  today: Date,
): { start: string; end: string } {
  switch (periodType) {
    case 'WEEKLY': {
      const start = startOfWeek(today, { weekStartsOn: 1 })
      const end = endOfWeek(today, { weekStartsOn: 1 })
      return { start: toIsoDate(start), end: toIsoDate(end) }
    }
    case 'MONTHLY':
      return { start: toIsoDate(startOfMonth(today)), end: toIsoDate(endOfMonth(today)) }
    case 'QUARTERLY':
      return { start: toIsoDate(startOfQuarter(today)), end: toIsoDate(endOfQuarter(today)) }
    case 'YEARLY':
      return { start: toIsoDate(startOfYear(today)), end: toIsoDate(endOfYear(today)) }
    case 'CUSTOM':
      return { start: toIsoDate(today), end: toIsoDate(addMonths(today, 1)) }
  }
}

export const budgetFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Enter a name').max(80, 'Max 80 characters'),
    categoryId: z.string(),
    periodType: z.enum(BUDGET_PERIOD_VALUES),
    customPeriodStart: z.string(),
    customPeriodEnd: z.string(),
    amount: z
      .string()
      .refine((value) => (parseAmountToMinor(value) ?? 0) > 0, 'Enter a positive amount'),
    currencyCode: z.string().regex(/^[A-Za-z]{3}$/, '3-letter currency code'),
    rolloverEnabled: z.boolean(),
    alertEnabled: z.boolean(),
    alertThresholdPct: z
      .string()
      .refine(
        (value) => /^\d{1,3}$/.test(value) && Number(value) >= 1 && Number(value) <= 100,
        '1–100%',
      ),
    includeSubcategories: z.boolean(),
    status: z.enum(['ACTIVE', 'ARCHIVED']),
  })
  .superRefine((values, ctx) => {
    if (values.periodType !== 'CUSTOM') return
    if (values.customPeriodStart === '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['customPeriodStart'],
        message: 'Pick a start date',
      })
      return
    }
    if (values.customPeriodEnd === '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['customPeriodEnd'],
        message: 'Pick an end date',
      })
      return
    }
    if (values.customPeriodEnd < values.customPeriodStart) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['customPeriodEnd'],
        message: 'End date must be on or after the start date',
      })
      return
    }
    const days =
      Math.round(
        (new Date(`${values.customPeriodEnd}T00:00:00`).getTime() -
          new Date(`${values.customPeriodStart}T00:00:00`).getTime()) /
          86_400_000,
      ) + 1
    if (days > MAX_CUSTOM_DAYS) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['customPeriodEnd'],
        message: 'Custom periods are at most two years',
      })
    }
  })

export type BudgetFormValues = z.infer<typeof budgetFormSchema>

export const EMPTY_BUDGET_FORM: BudgetFormValues = {
  name: '',
  categoryId: '',
  periodType: 'MONTHLY',
  customPeriodStart: '',
  customPeriodEnd: '',
  amount: '',
  currencyCode: '',
  rolloverEnabled: false,
  alertEnabled: true,
  alertThresholdPct: '80',
  includeSubcategories: true,
  status: 'ACTIVE',
}

export function budgetFormDefaults(budget: Budget): BudgetFormValues {
  return {
    name: budget.name,
    categoryId: budget.category ? String(budget.category.id) : '',
    periodType: budget.periodType,
    customPeriodStart: budget.periodStart,
    customPeriodEnd: budget.periodEnd,
    amount: formatMinor(budget.amountMinor, fractionDigitsFor(budget.currencyCode)),
    currencyCode: budget.currencyCode,
    rolloverEnabled: budget.rolloverEnabled,
    alertEnabled: budget.alertEnabled,
    alertThresholdPct: String(budget.alertThresholdPct),
    includeSubcategories: budget.includeSubcategories,
    status: budget.status,
  }
}

/**
 * The period a submission must use:
 *  - CUSTOM reads the two date fields;
 *  - a budget whose period type is unchanged keeps its existing window (so editing an
 *    amount or name never jumps the period forward);
 *  - anything else snaps to the canonical window of the new type around today.
 */
export function resolveBudgetWindow(
  values: Pick<BudgetFormValues, 'periodType' | 'customPeriodStart' | 'customPeriodEnd'>,
  original: Budget | null,
): { start: string; end: string } {
  if (values.periodType === 'CUSTOM') {
    return { start: values.customPeriodStart, end: values.customPeriodEnd }
  }
  if (original !== null && values.periodType === original.periodType) {
    return { start: original.periodStart, end: original.periodEnd }
  }
  return canonicalPeriod(values.periodType, new Date())
}

function toAmountMinor(values: BudgetFormValues): number {
  const amountMinor = parseAmountToMinor(values.amount, fractionDigitsFor(values.currencyCode))
  if (amountMinor === null || amountMinor <= 0) {
    throw new TypeError(`invalid amount: ${values.amount}`)
  }
  return amountMinor
}

export function toCreateBudgetInput(
  values: BudgetFormValues,
  original: Budget | null,
): CreateBudgetInput {
  const window = resolveBudgetWindow(values, original)
  return {
    name: values.name,
    categoryId: values.categoryId === '' ? null : Number(values.categoryId),
    periodType: values.periodType,
    periodStart: window.start,
    periodEnd: window.end,
    amountMinor: toAmountMinor(values),
    currencyCode: values.currencyCode.toUpperCase(),
    rolloverEnabled: values.rolloverEnabled,
    alertThresholdPct: Number(values.alertThresholdPct),
    alertEnabled: values.alertEnabled,
    includeSubcategories: values.includeSubcategories,
  }
}

export function toUpdateBudgetInput(values: BudgetFormValues, budget: Budget): UpdateBudgetInput {
  const window = resolveBudgetWindow(values, budget)
  return {
    name: values.name,
    categoryId: values.categoryId === '' ? undefined : Number(values.categoryId),
    periodType: values.periodType,
    periodStart: window.start,
    periodEnd: window.end,
    amountMinor: toAmountMinor(values),
    currencyCode: values.currencyCode.toUpperCase(),
    rolloverEnabled: values.rolloverEnabled,
    alertThresholdPct: Number(values.alertThresholdPct),
    alertEnabled: values.alertEnabled,
    includeSubcategories: values.includeSubcategories,
    status: values.status as BudgetStatus,
    version: budget.version,
  }
}