import { z } from 'zod'
import { toIsoDate } from '@/lib/dates'
import { formatMinor, fractionDigitsFor, parseAmountToMinor } from '@/lib/money'
import type { GoalStatus } from '@/types/enums'
import type { CreateContributionInput, CreateGoalInput, Goal, UpdateGoalInput } from './types'

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export const GOAL_PRIORITY_VALUES = ['0', '1', '2', '3', '4', '5'] as const

export const goalFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Enter a name').max(80, 'Max 80 characters'),
    description: z.string().trim().max(500, 'Max 500 characters'),
    targetAmount: z
      .string()
      .refine((value) => (parseAmountToMinor(value) ?? 0) > 0, 'Enter a positive target'),
    currencyCode: z.string().regex(/^[A-Za-z]{3}$/, '3-letter currency code'),
    targetDate: z.string().regex(/^$|^\d{4}-\d{2}-\d{2}$/, 'Pick a date'),
    linkedAccountId: z.string(),
    priority: z.enum(GOAL_PRIORITY_VALUES),
    icon: z.string().trim().max(40, 'Max 40 characters'),
    color: z.string().regex(/^$|^#[0-9A-Fa-f]{6}$/, 'Hex colour like #1E88E5'),
    milestonePct: z
      .string()
      .refine(
        (value) => /^\d{1,3}$/.test(value) && Number(value) >= 1 && Number(value) <= 100,
        '1–100%',
      ),
    status: z.enum(['ACTIVE', 'PAUSED', 'CANCELLED']),
  })
  .superRefine((values, ctx) => {
    if (values.targetDate === '') return
    if (values.targetDate < toIsoDate(new Date())) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['targetDate'],
        message: 'Target date must be today or later',
      })
    }
  })

export type GoalFormValues = z.infer<typeof goalFormSchema>

export const EMPTY_GOAL_FORM: GoalFormValues = {
  name: '',
  description: '',
  targetAmount: '',
  currencyCode: '',
  targetDate: '',
  linkedAccountId: '',
  priority: '2',
  icon: '',
  color: '',
  milestonePct: '90',
  status: 'ACTIVE',
}

export function goalFormDefaults(goal: Goal): GoalFormValues {
  return {
    name: goal.name,
    description: goal.description ?? '',
    targetAmount: formatMinor(goal.targetAmountMinor, fractionDigitsFor(goal.currencyCode)),
    currencyCode: goal.currencyCode,
    targetDate: goal.targetDate ?? '',
    linkedAccountId: goal.linkedAccountId !== null ? String(goal.linkedAccountId) : '',
    priority: String(goal.priority) as GoalFormValues['priority'],
    icon: goal.icon ?? '',
    color: goal.color ?? '',
    milestonePct: '90',
    status: goal.status === 'COMPLETED' ? 'ACTIVE' : (goal.status as GoalFormValues['status']),
  }
}

export function toCreateGoalInput(values: GoalFormValues): CreateGoalInput {
  const targetAmountMinor = parseAmountToMinor(values.targetAmount, fractionDigitsFor(values.currencyCode))
  if (targetAmountMinor === null || targetAmountMinor <= 0) {
    throw new TypeError(`invalid target: ${values.targetAmount}`)
  }
  return {
    name: values.name,
    description: values.description === '' ? null : values.description,
    targetAmountMinor,
    currencyCode: values.currencyCode.toUpperCase(),
    targetDate: values.targetDate === '' ? null : values.targetDate,
    linkedAccountId: values.linkedAccountId === '' ? null : Number(values.linkedAccountId),
    priority: Number(values.priority),
    icon: values.icon === '' ? null : values.icon,
    color: values.color === '' ? null : values.color,
    milestonePct: Number(values.milestonePct),
  }
}

export function toUpdateGoalInput(values: GoalFormValues, goal: Goal): UpdateGoalInput {
  const targetAmountMinor = parseAmountToMinor(values.targetAmount, fractionDigitsFor(values.currencyCode))
  if (targetAmountMinor === null || targetAmountMinor <= 0) {
    throw new TypeError(`invalid target: ${values.targetAmount}`)
  }
  const clearingTargetDate = values.targetDate === '' && goal.targetDate !== null
  const clearingLinkedAccount = values.linkedAccountId === '' && goal.linkedAccountId !== null
  return {
    name: values.name,
    description: values.description === '' ? '' : values.description,
    targetAmountMinor,
    targetDate: values.targetDate === '' ? undefined : values.targetDate,
    clearTargetDate: clearingTargetDate ? true : undefined,
    linkedAccountId: values.linkedAccountId === '' ? undefined : Number(values.linkedAccountId),
    clearLinkedAccount: clearingLinkedAccount ? true : undefined,
    priority: Number(values.priority),
    icon: values.icon === '' ? '' : values.icon,
    color: values.color === '' ? null : values.color,
    milestonePct: Number(values.milestonePct),
    status: values.status as GoalStatus,
    version: goal.version,
  }
}

export const contributionFormSchema = z
  .object({
    amount: z
      .string()
      .refine((value) => {
        const minor = parseAmountToMinor(value)
        return minor !== null && minor !== 0
      }, 'Enter an amount'),
    contributedOn: z.string().regex(DATE_PATTERN, 'Pick a date'),
    method: z.enum(['MANUAL', 'TRANSFER', 'ADJUSTMENT']),
    notes: z.string().trim().max(500, 'Max 500 characters'),
  })
  .superRefine((values, ctx) => {
    const minor = parseAmountToMinor(values.amount) ?? 0
    if (minor < 0 && values.notes === '') {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['notes'],
        message: 'Withdrawals need a reason',
      })
    }
  })

export type ContributionFormValues = z.infer<typeof contributionFormSchema>

export const EMPTY_CONTRIBUTION_FORM: ContributionFormValues = {
  amount: '',
  contributedOn: toIsoDate(new Date()),
  method: 'MANUAL',
  notes: '',
}

export function toCreateContributionInput(
  values: ContributionFormValues,
  goal: Goal,
): CreateContributionInput {
  const amountMinor = parseAmountToMinor(values.amount, fractionDigitsFor(goal.currencyCode))
  if (amountMinor === null || amountMinor === 0) {
    throw new TypeError(`invalid amount: ${values.amount}`)
  }
  if (values.method === 'TRANSFER' && goal.linkedAccountId === null) {
    throw new TypeError('link an account to the goal before contributing by transfer')
  }
  return {
    amountMinor,
    contributedOn: values.contributedOn,
    method: values.method,
    transactionId: null,
    notes: values.notes === '' ? null : values.notes,
  }
}