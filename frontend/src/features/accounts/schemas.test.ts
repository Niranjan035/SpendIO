import { describe, expect, it } from 'vitest'
import {
  accountFormSchema,
  adjustBalanceSchema,
  editAccountSchema,
  toAdjustBalanceInput,
  toCreateAccountInput,
  toUpdateAccountInput,
} from './schemas'

function validCreate(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    name: 'Everyday checking',
    type: 'BANK',
    currencyCode: 'usd',
    openingBalance: '100.00',
    openingBalanceDate: '2026-03-01',
    openingTransactionDate: '2026-03-01',
    createOpeningBalanceTransaction: true,
    primary: true,
    institution: '',
    last4: '',
    creditLimit: '',
    color: '',
    notes: '',
    ...overrides,
  }
}

describe('accountFormSchema', () => {
  it('rejects a blank name', () => {
    const { error } = accountFormSchema.safeParse(validCreate({ name: '   ' }))
    expect(error?.issues.some((issue) => issue.path[0] === 'name')).toBe(true)
  })

  it('rejects a malformed currency and normalises the case', () => {
    const bad = accountFormSchema.safeParse(validCreate({ currencyCode: 'D' }))
    expect(bad.success).toBe(false)

    const data = accountFormSchema.parse(validCreate())
    expect(data.currencyCode).toBe('USD')
  })

  it('requires an opening date once an opening amount is typed', () => {
    const { error } = accountFormSchema.safeParse(
      validCreate({ openingBalance: '150.00', openingBalanceDate: '' }),
    )
    expect(error?.issues.some((issue) => issue.path[0] === 'openingBalanceDate')).toBe(true)
  })

  it('accepts a zero opening balance without a date', () => {
    const { success } = accountFormSchema.safeParse(
      validCreate({ openingBalance: '0', openingBalanceDate: '' }),
    )
    expect(success).toBe(true)
  })

  it('rejects credit-card identifiers that are not 4 digits', () => {
    const { error } = accountFormSchema.safeParse(validCreate({ last4: '12' }))
    expect(error?.issues.some((issue) => issue.path[0] === 'last4')).toBe(true)
  })
})

describe('editAccountSchema', () => {
  it('keeps optional strings blank when untouched', () => {
    const { success, data } = editAccountSchema.safeParse({
      name: 'Everyday checking',
      institution: '',
      last4: '',
      color: '',
      notes: '',
    })
    expect(success).toBe(true)
    expect(data?.institution).toBe('')
  })
})

describe('toCreateAccountInput', () => {
  it('maps form values onto the API payload', () => {
    const input = toCreateAccountInput(accountFormSchema.parse(validCreate()))
    expect(input).toMatchObject({
      name: 'Everyday checking',
      type: 'BANK',
      currencyCode: 'USD',
      openingBalanceMinor: 10000,
      openingBalanceDate: '2026-03-01',
      primary: true,
      openingTransactionDate: '2026-03-01',
      createOpeningBalanceTransaction: true,
      creditLimitMinor: null,
      billingDay: null,
      dueDay: null,
    })
  })

  it('omits the opening transaction when the amount is zero', () => {
    const input = toCreateAccountInput(
      accountFormSchema.parse(validCreate({ openingBalance: '0' })),
    )
    expect(input.openingBalanceMinor).toBe(0)
    expect(input.createOpeningBalanceTransaction).toBe(false)
    expect(input.openingTransactionDate).toBeNull()
  })

  it('honours zero-decimal currencies for the opening amount', () => {
    const input = toCreateAccountInput(
      accountFormSchema.parse(validCreate({ currencyCode: 'jpy', openingBalance: '1500' })),
    )
    expect(input.openingBalanceMinor).toBe(1500)
    expect(input.currencyCode).toBe('JPY')
  })

  it('sends null, not blank strings, for empty optional fields', () => {
    const input = toCreateAccountInput(
      accountFormSchema.parse(validCreate({ institution: '', last4: '', color: '', notes: '' })),
    )
    expect(input.institution).toBeNull()
    expect(input.last4).toBeNull()
    expect(input.color).toBeNull()
    expect(input.notes).toBeNull()
  })

  it('maps the reported Cash/INR creation onto a valid payload', () => {
    const input = toCreateAccountInput(
      accountFormSchema.parse(
        validCreate({
          name: 'Check-in',
          type: 'CASH',
          currencyCode: 'INR',
          openingBalance: '4500',
          openingBalanceDate: '2026-10-06',
          openingTransactionDate: '',
          createOpeningBalanceTransaction: true,
          primary: true,
          institution: '',
          last4: '',
          creditLimit: '',
          color: '#12AB34',
          notes: 'checkin daily',
        }),
      ),
    )
    expect(input).toEqual({
      name: 'Check-in',
      type: 'CASH',
      currencyCode: 'INR',
      openingBalanceMinor: 450000,
      openingBalanceDate: '2026-10-06',
      institution: null,
      last4: null,
      creditLimitMinor: null,
      billingDay: null,
      dueDay: null,
      icon: null,
      color: '#12AB34',
      notes: 'checkin daily',
      primary: true,
      openingTransactionDate: null,
      createOpeningBalanceTransaction: false,
    })
  })
})

describe('toUpdateAccountInput', () => {
  it('sends null for blank optional edit fields', () => {
    const input = toUpdateAccountInput(
      editAccountSchema.parse({
        name: 'Check-in',
        institution: '',
        last4: '',
        color: '',
        notes: '',
      }),
    )
    expect(input).toEqual({
      name: 'Check-in',
      institution: null,
      last4: null,
      color: null,
      notes: null,
    })
  })
})

describe('adjustBalanceSchema', () => {
  it('parses the new balance to minor units', () => {
    const input = toAdjustBalanceInput(
      adjustBalanceSchema.parse({
        newBalance: '99.99',
        occurredOn: '2026-03-01',
        reason: 'correction',
      }),
      'USD',
    )
    expect(input).toEqual({
      newBalanceMinor: 9999,
      occurredOn: '2026-03-01',
      reason: 'correction',
    })
  })

  it('rejects an unparseable balance and a blank reason', () => {
    const badBalance = adjustBalanceSchema.safeParse({
      newBalance: 'abc',
      occurredOn: '2026-03-01',
      reason: 'correction',
    })
    const badReason = adjustBalanceSchema.safeParse({
      newBalance: '10',
      occurredOn: '2026-03-01',
      reason: '   ',
    })
    expect(badBalance.success).toBe(false)
    expect(badReason.success).toBe(false)
  })
})
