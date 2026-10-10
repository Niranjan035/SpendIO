import { describe, expect, it } from 'vitest'
import {
  browserDefaults,
  describePolicyViolation,
  loginSchema,
  passwordSchema,
  registerSchema,
} from './schemas'

describe('passwordSchema', () => {
  const valid = 'Str0ngPassphrase1'

  it('accepts a password that meets every rule', () => {
    expect(passwordSchema.safeParse(valid).success).toBe(true)
  })

  it('caps passwords at the backend BCrypt limit of 72 characters', () => {
    const atLimit = `A1a${'x'.repeat(69)}`
    expect(atLimit).toHaveLength(72)
    expect(passwordSchema.safeParse(atLimit).success).toBe(true)

    const overLimit = `A1a${'x'.repeat(70)}`
    expect(overLimit).toHaveLength(73)
    expect(passwordSchema.safeParse(overLimit).success).toBe(false)
  })

  it('rejects short passwords and missing character classes', () => {
    expect(passwordSchema.safeParse('short').success).toBe(false)
    expect(passwordSchema.safeParse('alllowercase1').success).toBe(false)
    expect(passwordSchema.safeParse('ALLUPPERCASE1').success).toBe(false)
    expect(passwordSchema.safeParse('NoDigitsHere!').success).toBe(false)
  })
})

describe('registerSchema', () => {
  const valid = {
    email: 'ana@example.com',
    password: 'Str0ngPassphrase1',
    firstName: 'Ana',
    lastName: 'Lopes',
    timezone: 'Europe/Lisbon',
    locale: 'en-GB',
    baseCurrencyCode: 'EUR',
  }

  it('accepts the backend RegisterRequest shape', () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
  })

  it('trims the names', () => {
    const parsed = registerSchema.safeParse({ ...valid, firstName: '  Ana  ' })
    expect(parsed.success && parsed.data.firstName).toBe('Ana')
  })

  it('requires both names and a three-letter currency code', () => {
    expect(registerSchema.safeParse({ ...valid, firstName: '   ' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...valid, lastName: '' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...valid, baseCurrencyCode: 'EURO' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...valid, timezone: '' }).success).toBe(false)
  })
})

describe('loginSchema', () => {
  it('requires a password but does not enforce the policy (existing accounts)', () => {
    expect(loginSchema.safeParse({ email: 'a@b.co', password: 'x' }).success).toBe(true)
    expect(loginSchema.safeParse({ email: 'a@b.co', password: '' }).success).toBe(false)
    expect(loginSchema.safeParse({ email: 'nope', password: 'x' }).success).toBe(false)
  })
})

describe('describePolicyViolation', () => {
  it('expands the dynamic length rules', () => {
    expect(describePolicyViolation('at-least-10-characters')).toBe('Use at least 10 characters')
    expect(describePolicyViolation('at-most-72-characters')).toBe('Use at most 72 characters')
  })

  it('expands the fixed rule ids', () => {
    expect(describePolicyViolation('a-lowercase-letter')).toBe('Include a lowercase letter')
    expect(describePolicyViolation('an-uppercase-letter')).toBe('Include an uppercase letter')
    expect(describePolicyViolation('a-digit')).toBe('Include a digit')
    expect(describePolicyViolation('not-your-email-address')).toBe(
      'Password cannot be your email address',
    )
    expect(describePolicyViolation('not-a-known-breached-password')).toContain('breaches')
  })

  it('falls back for unknown ids', () => {
    expect(describePolicyViolation('mystery-rule')).toBe('Password does not meet the policy')
  })
})

describe('browserDefaults', () => {
  it('returns a timezone and a locale the backend accepts', () => {
    const { timezone, locale } = browserDefaults()
    expect(timezone).toBeTruthy()
    expect(locale.length).toBeGreaterThan(0)
    expect(locale.length).toBeLessThanOrEqual(16)
  })
})
