import { describe, expect, it } from 'vitest'
import {
  DATE_FORMAT_OPTIONS,
  FIRST_DAY_OPTIONS,
  baseCurrencySchema,
  changePasswordSchema,
  deleteAccountSchema,
  preferencesSchema,
  profileSchema,
} from './schemas'

describe('profileSchema', () => {
  it('accepts an empty form as "leave unchanged"', () => {
    const result = profileSchema.safeParse({
      firstName: '',
      lastName: '',
      timezone: '',
      locale: '',
    })
    expect(result.success).toBe(true)
  })

  it('trims and accepts real values', () => {
    const result = profileSchema.safeParse({
      firstName: '  Ana ',
      lastName: 'Lopes',
      timezone: 'Europe/Lisbon',
      locale: 'en-GB',
    })
    expect(result.success).toBe(true)
  })

  it('rejects malformed timezone and locale', () => {
    expect(
      profileSchema.safeParse({
        firstName: '',
        lastName: '',
        timezone: 'Europe Lisbon',
        locale: 'en-GB',
      }).success,
    ).toBe(false)
    expect(
      profileSchema.safeParse({
        firstName: '',
        lastName: '',
        timezone: 'Europe/Lisbon',
        locale: 'EN',
      }).success,
    ).toBe(false)
  })

  it('enforces the 80 character backend cap', () => {
    const long = 'a'.repeat(81)
    const result = profileSchema.safeParse({
      firstName: long,
      lastName: '',
      timezone: '',
      locale: '',
    })
    expect(result.success).toBe(false)
  })
})

describe('preferencesSchema', () => {
  const valid = {
    theme: 'SYSTEM' as const,
    dateFormat: 'yyyy-MM-dd',
    firstDayOfWeek: 1,
    decimalStyle: 'DOT' as const,
    groupSeparator: ',',
    currencySymbolPosition: 'BEFORE' as const,
    numberFormat: '#,##0.00',
    budgetAlertThresholdPct: 80,
    goalReminderDays: 30,
    recurringReminderDays: 3,
    emailDigestEnabled: false,
    reducedMotion: false,
  }

  it('accepts the backend defaults', () => {
    expect(preferencesSchema.safeParse(valid).success).toBe(true)
  })

  it('mirrors the PreferenceService ranges', () => {
    const cases = [
      { budgetAlertThresholdPct: 0 },
      { budgetAlertThresholdPct: 101 },
      { goalReminderDays: -1 },
      { goalReminderDays: 366 },
      { recurringReminderDays: -1 },
      { recurringReminderDays: 31 },
      { firstDayOfWeek: 0 },
      { firstDayOfWeek: 8 },
    ] as const

    for (const change of cases) {
      const result = preferencesSchema.safeParse({ ...valid, ...change })
      expect(result.success, JSON.stringify(change)).toBe(false)
    }
  })

  it('accepts the boundary values', () => {
    const result = preferencesSchema.safeParse({
      ...valid,
      budgetAlertThresholdPct: 100,
      goalReminderDays: 365,
      recurringReminderDays: 30,
      firstDayOfWeek: 7,
    })
    expect(result.success).toBe(true)
  })

  it('rejects non-numeric reminder fields', () => {
    const result = preferencesSchema.safeParse({ ...valid, goalReminderDays: Number.NaN })
    expect(result.success).toBe(false)
  })
})

describe('baseCurrencySchema', () => {
  it('accepts a three letter code', () => {
    expect(baseCurrencySchema.safeParse({ currencyCode: 'EUR' }).success).toBe(true)
  })

  it('rejects anything else', () => {
    for (const currencyCode of ['', 'eu', 'EURO', 'EUR!', 'E']) {
      expect(baseCurrencySchema.safeParse({ currencyCode }).success).toBe(false)
    }
  })
})

describe('changePasswordSchema', () => {
  it('accepts a matching pair within the backend length rule', () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: 'old-secret',
      newPassword: 'brand-new-secret',
      confirmPassword: 'brand-new-secret',
    })
    expect(result.success).toBe(true)
  })

  it('rejects a short new password', () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: 'old-secret',
      newPassword: 'short',
      confirmPassword: 'short',
    })
    expect(result.success).toBe(false)
  })

  it('rejects a mismatch on the confirmation field', () => {
    const result = changePasswordSchema.safeParse({
      currentPassword: 'old-secret',
      newPassword: 'brand-new-secret',
      confirmPassword: 'different',
    })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0]?.path).toEqual(['confirmPassword'])
  })
})

describe('deleteAccountSchema', () => {
  it('requires the exact DELETE phrase', () => {
    expect(deleteAccountSchema.safeParse({ password: 'p', confirmationText: 'delete' }).success).toBe(
      false,
    )
    expect(deleteAccountSchema.safeParse({ password: 'p', confirmationText: 'DELETE' }).success).toBe(
      true,
    )
  })

  it('requires the account password', () => {
    expect(deleteAccountSchema.safeParse({ password: '', confirmationText: 'DELETE' }).success).toBe(
      false,
    )
  })
})

describe('form option lists', () => {
  it('offers date formats within the backend 20 char limit', () => {
    for (const format of DATE_FORMAT_OPTIONS) {
      expect(format.length).toBeLessThanOrEqual(20)
      expect(preferencesSchema.shape.dateFormat.safeParse(format).success).toBe(true)
    }
  })

  it('offers ISO-8601 first days of week', () => {
    expect(FIRST_DAY_OPTIONS.map((day) => day.value)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })
})
