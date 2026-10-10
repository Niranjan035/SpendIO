import { describe, expect, it } from 'vitest'
import {
  EMPTY_REVERSE_FORM,
  EMPTY_TRANSACTION_FORM,
  EMPTY_TRANSFER_FORM,
  reverseFormSchema,
  toCreateTransactionInput,
  toCreateTransferInput,
  toUpdateTransactionInput,
  transactionFormSchema,
  transferFormSchema,
  type TransactionFormValues,
  type TransferFormValues,
} from './schemas'

function expenseForm(overrides: Partial<TransactionFormValues> = {}): TransactionFormValues {
  return {
    ...EMPTY_TRANSACTION_FORM,
    accountId: '1',
    type: 'EXPENSE',
    amount: '12.34',
    occurredOn: '2026-03-01',
    categoryId: '9',
    description: 'Coffee',
    ...overrides,
  }
}

function issuePaths(result: ReturnType<typeof transactionFormSchema.safeParse>): string[] {
  return result.success ? [] : result.error.issues.map((issue) => String(issue.path[0]))
}

describe('transactionFormSchema', () => {
  it('accepts a complete expense', () => {
    expect(transactionFormSchema.safeParse(expenseForm()).success).toBe(true)
  })

  it('requires a category for income and expense', () => {
    const result = transactionFormSchema.safeParse(expenseForm({ categoryId: '' }))
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('categoryId')
  })

  it('accepts adjustments without a category', () => {
    const result = transactionFormSchema.safeParse(
      expenseForm({ type: 'ADJUSTMENT', categoryId: '', direction: 'INFLOW' }),
    )
    expect(result.success).toBe(true)
  })

  it('forbids categories on adjustments', () => {
    const result = transactionFormSchema.safeParse(expenseForm({ type: 'ADJUSTMENT' }))
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('categoryId')
  })

  it('rejects VOID status for income and expense', () => {
    const result = transactionFormSchema.safeParse(expenseForm({ status: 'VOID' }))
    expect(result.success).toBe(false)
    expect(issuePaths(result)).toContain('status')
  })

  it('allows VOID status for adjustments', () => {
    const result = transactionFormSchema.safeParse(
      expenseForm({ type: 'ADJUSTMENT', categoryId: '', status: 'VOID' }),
    )
    expect(result.success).toBe(true)
  })

  it('rejects missing, zero and negative amounts', () => {
    for (const amount of ['', '0', '-3']) {
      expect(transactionFormSchema.safeParse(expenseForm({ amount })).success).toBe(false)
    }
  })
})

describe('toCreateTransactionInput', () => {
  it('derives sign and status for an expense and freezes the account currency', () => {
    const input = toCreateTransactionInput(expenseForm(), { currencyCode: 'USD' })

    expect(input).toMatchObject({
      accountId: 1,
      categoryId: 9,
      type: 'EXPENSE',
      direction: null,
      amountMinor: 1234,
      currencyCode: 'USD',
      exchangeRate: null,
      status: 'POSTED',
      dedupeKey: null,
      description: 'Coffee',
      merchant: null,
      payee: null,
      notes: null,
    })
  })

  it('parses the amount with the currency fraction digits', () => {
    const jpy = toCreateTransactionInput(expenseForm({ amount: '500' }), { currencyCode: 'JPY' })
    expect(jpy.amountMinor).toBe(500)
  })

  it('forwards adjustment direction and status without a category', () => {
    const input = toCreateTransactionInput(
      expenseForm({
        type: 'ADJUSTMENT',
        categoryId: '',
        direction: 'INFLOW',
        status: 'PENDING',
        description: '',
      }),
      { currencyCode: 'USD' },
    )

    expect(input).toMatchObject({
      categoryId: null,
      direction: 'INFLOW',
      status: 'PENDING',
      description: null,
    })
  })

  it('throws on an unparseable amount', () => {
    expect(() =>
      toCreateTransactionInput(expenseForm({ amount: 'abc' }), { currencyCode: 'USD' }),
    ).toThrow(TypeError)
  })
})

describe('toUpdateTransactionInput', () => {
  it('maps the editable PATCH fields and echoes the version', () => {
    const values = expenseForm({ description: '', notes: 'weekly shop' })

    expect(toUpdateTransactionInput(values, 'EUR', 4)).toEqual({
      accountId: 1,
      categoryId: 9,
      amountMinor: 1234,
      currencyCode: 'EUR',
      occurredOn: '2026-03-01',
      description: '',
      notes: 'weekly shop',
      version: 4,
    })
  })

  it('clears the category when the form has none', () => {
    const values = expenseForm({ type: 'ADJUSTMENT', categoryId: '' })
    expect(toUpdateTransactionInput(values, 'USD', 0).categoryId).toBeNull()
  })
})

function transferForm(overrides: Partial<TransferFormValues> = {}): TransferFormValues {
  return {
    ...EMPTY_TRANSFER_FORM,
    fromAccountId: '1',
    toAccountId: '2',
    amount: '20.00',
    occurredOn: '2026-03-01',
    description: 'Rent share',
    notes: 'with interest',
    ...overrides,
  }
}

function transferIssuePaths(result: ReturnType<typeof transferFormSchema.safeParse>): string[] {
  return result.success ? [] : result.error.issues.map((issue) => String(issue.path[0]))
}

describe('transferFormSchema', () => {
  it('accepts a transfer between distinct accounts', () => {
    expect(transferFormSchema.safeParse(transferForm()).success).toBe(true)
  })

  it('rejects the same source and destination account', () => {
    const result = transferFormSchema.safeParse(transferForm({ toAccountId: '1' }))
    expect(result.success).toBe(false)
    expect(transferIssuePaths(result)).toContain('toAccountId')
  })

  it('requires an account on both sides', () => {
    const result = transferFormSchema.safeParse(transferForm({ fromAccountId: '' }))
    expect(result.success).toBe(false)
    expect(transferIssuePaths(result)).toContain('fromAccountId')
  })

  it('rejects missing, zero and negative amounts', () => {
    for (const amount of ['', '0', '-3']) {
      expect(transferFormSchema.safeParse(transferForm({ amount })).success).toBe(false)
    }
  })
})

describe('toCreateTransferInput', () => {
  it('states the amount in the source account currency', () => {
    const input = toCreateTransferInput(transferForm({ amount: '25.50' }), { currencyCode: 'EUR' })

    expect(input).toEqual({
      fromAccountId: 1,
      toAccountId: 2,
      amountMinor: 2550,
      currencyCode: 'EUR',
      toAmountMinor: null,
      occurredOn: '2026-03-01',
      description: 'Rent share',
      notes: 'with interest',
    })
  })

  it('parses the amount with the currency fraction digits', () => {
    const jpy = toCreateTransferInput(transferForm({ amount: '500' }), { currencyCode: 'JPY' })
    expect(jpy.amountMinor).toBe(500)
  })

  it('nulls blank description and notes', () => {
    const input = toCreateTransferInput(transferForm({ description: '', notes: '' }), {
      currencyCode: 'USD',
    })
    expect(input.description).toBeNull()
    expect(input.notes).toBeNull()
  })

  it('throws on an unparseable amount', () => {
    expect(() =>
      toCreateTransferInput(transferForm({ amount: 'abc' }), { currencyCode: 'USD' }),
    ).toThrow(TypeError)
  })
})

describe('reverseFormSchema', () => {
  it('accepts a reason and a date', () => {
    expect(
      reverseFormSchema.safeParse({ reason: 'Duplicate charge', occurredOn: '2026-03-01' }).success,
    ).toBe(true)
  })

  it('requires a non-blank reason', () => {
    for (const reason of ['', '   ']) {
      const result = reverseFormSchema.safeParse({ reason, occurredOn: '2026-03-01' })
      expect(result.success).toBe(false)
      expect(result.success ? [] : result.error.issues.map((i) => String(i.path[0]))).toContain(
        'reason',
      )
    }
  })

  it('requires a date', () => {
    const result = reverseFormSchema.safeParse({ reason: 'Oops', occurredOn: '' })
    expect(result.success).toBe(false)
  })

  it('starts with today for the default form', () => {
    expect(EMPTY_REVERSE_FORM).toMatchObject({ reason: '' })
    expect(EMPTY_REVERSE_FORM.occurredOn).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
