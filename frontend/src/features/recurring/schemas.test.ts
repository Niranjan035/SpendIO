import { describe, expect, it } from 'vitest'
import {
  EMPTY_RECURRING_FORM,
  recurringFormSchema,
  recurringFormDefaults,
  toCreateRecurringInput,
  toUpdateRecurringInput,
  type RecurringFormValues,
} from './schemas'
import type { RecurringTransaction } from './types'

function form(overrides: Partial<RecurringFormValues> = {}): RecurringFormValues {
  return {
    ...EMPTY_RECURRING_FORM,
    accountId: '1',
    type: 'EXPENSE',
    categoryId: '9',
    amount: '12.34',
    frequency: 'MONTHLY',
    dayOfMonth: '1',
    startDate: '2026-03-01',
    ...overrides,
  }
}

function issuePaths(result: ReturnType<typeof recurringFormSchema.safeParse>): string[] {
  return result.success ? [] : result.error.issues.map((issue) => String(issue.path[0]))
}

const account = { id: 1, currencyCode: 'USD' }

const rule: RecurringTransaction = {
  id: 7,
  account: { id: 1, name: 'Checking', currencyCode: 'USD' },
  toAccount: null,
  category: { id: 9, name: 'Rent', type: 'EXPENSE', color: null, icon: null },
  type: 'EXPENSE',
  direction: 'OUTFLOW',
  amountMinor: 1234,
  currencyCode: 'USD',
  baseAmountMinor: 1234,
  description: 'Rent',
  notes: null,
  frequency: 'MONTHLY',
  intervalCount: 1,
  dayOfMonth: 1,
  dayOfWeek: null,
  skipWeekends: false,
  startDate: '2026-03-01',
  endDate: null,
  targetOccurrenceDate: null,
  nextRunDate: '2026-04-01',
  lastRunDate: null,
  status: 'ACTIVE',
  autoPost: true,
  maxRetries: 3,
  retryCount: 0,
  lastError: null,
  failureReason: null,
  generatedCount: 1,
  createdAt: '2026-03-01T09:00:00Z',
}

describe('recurringFormSchema', () => {
  it('accepts a complete monthly expense', () => {
    expect(recurringFormSchema.safeParse(form()).success).toBe(true)
  })

  it('requires a category for income and expense', () => {
    const result = recurringFormSchema.safeParse(form({ categoryId: '' }))
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('categoryId')
  })

  it('accepts a transfer with a destination and no category', () => {
    const result = recurringFormSchema.safeParse(
      form({ type: 'TRANSFER', toAccountId: '2', categoryId: '' }),
    )
    expect(result.success).toBe(true)
  })

  it('requires a destination account for transfers', () => {
    const result = recurringFormSchema.safeParse(form({ type: 'TRANSFER', categoryId: '' }))
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('toAccountId')
  })

  it('forbids categories on transfers and destinations elsewhere', () => {
    const withCategory = recurringFormSchema.safeParse(
      form({ type: 'TRANSFER', toAccountId: '2', categoryId: '9' }),
    )
    expect(issuePaths(withCategory)).toContain('categoryId')

    const withDestination = recurringFormSchema.safeParse(
      form({ type: 'EXPENSE', toAccountId: '2' }),
    )
    expect(issuePaths(withDestination)).toContain('toAccountId')
  })

  it('rejects transferring into the same account', () => {
    const result = recurringFormSchema.safeParse(form({ type: 'TRANSFER', toAccountId: '1' }))
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('toAccountId')
  })

  it('requires a day of month for monthly rules', () => {
    const result = recurringFormSchema.safeParse(form({ dayOfMonth: '' }))
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('dayOfMonth')
  })

  it('requires a day of week for weekly rules and ignores the day of month', () => {
    const missing = recurringFormSchema.safeParse(
      form({ frequency: 'WEEKLY', dayOfWeek: '' }),
    )
    expect(issuePaths(missing)).toContain('dayOfWeek')

    const present = recurringFormSchema.safeParse(
      form({ frequency: 'WEEKLY', dayOfWeek: '3', dayOfMonth: '5' }),
    )
    expect(issuePaths(present)).toContain('dayOfMonth')
    expect(present.success).toBe(false)
  })

  it('rejects day fields on daily and yearly rules', () => {
    const daily = recurringFormSchema.safeParse(form({ frequency: 'DAILY', dayOfMonth: '5' }))
    expect(issuePaths(daily)).toContain('dayOfMonth')

    const yearly = recurringFormSchema.safeParse(form({ frequency: 'YEARLY', dayOfWeek: '3' }))
    expect(issuePaths(yearly)).toContain('dayOfWeek')
  })

  it('requires the anchor payday for custom rules', () => {
    const missing = recurringFormSchema.safeParse(form({ frequency: 'CUSTOM', targetOccurrenceDate: '' }))
    expect(issuePaths(missing)).toContain('targetOccurrenceDate')
  })

  it('rejects an end date before the start date', () => {
    const result = recurringFormSchema.safeParse(
      form({ startDate: '2026-03-10', endDate: '2026-03-01' }),
    )
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('endDate')
  })

  it('bounds the retry budget to 1–99', () => {
    expect(recurringFormSchema.safeParse(form({ maxRetries: '0' })).success).toBe(false)
    expect(recurringFormSchema.safeParse(form({ maxRetries: '100' })).success).toBe(false)
    expect(recurringFormSchema.safeParse(form({ maxRetries: '3' })).success).toBe(true)
  })
})

describe('toCreateRecurringInput', () => {
  it('parses the amount in the account currency', () => {
    const input = toCreateRecurringInput(form({ amount: '1,234.56' }), account)
    expect(input).toMatchObject({ amountMinor: 123456, currencyCode: 'USD' })
  })

  it('maps transfers with a destination and no category', () => {
    const input = toCreateRecurringInput(
      form({ type: 'TRANSFER', toAccountId: '2', categoryId: '' }),
      account,
    )
    expect(input).toMatchObject({
      accountId: 1,
      toAccountId: 2,
      categoryId: null,
      type: 'TRANSFER',
      amountMinor: 1234,
    })
  })

  it('sends nulls for blank optional fields', () => {
    const input = toCreateRecurringInput(
      form({ description: '', notes: '', endDate: '', targetOccurrenceDate: '' }),
      account,
    )
    expect(input).toMatchObject({
      description: null,
      notes: null,
      endDate: null,
      targetOccurrenceDate: null,
    })
  })
})

describe('toUpdateRecurringInput', () => {
  it('echoes the form and carries the version', () => {
    const input = toUpdateRecurringInput(
      form({ description: '', notes: 'short' }),
      account,
      4,
    )
    expect(input.version).toBe(4)
    expect(input.description).toBe('')
    expect(input.notes).toBe('short')
  })
})

describe('recurringFormDefaults', () => {
  it('round-trips an existing rule and fills schedule day fields', () => {
    const defaults = recurringFormDefaults(rule)
    expect(defaults.accountId).toBe('1')
    expect(defaults.amount).toBe('12.34')
    expect(defaults.dayOfMonth).toBe('1')
    expect(defaults.frequency).toBe('MONTHLY')
  })

  it('derives the day of month from the start date when the rule has none', () => {
    const withoutDay = { ...rule, dayOfMonth: null, startDate: '2026-03-15' }
    expect(recurringFormDefaults(withoutDay).dayOfMonth).toBe('15')
  })
})