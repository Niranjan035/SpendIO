import { describe, expect, it } from 'vitest'
import {
  convertMinor,
  decimalToMinor,
  formatMinor,
  formatMoney,
  fractionDigitsFor,
  parseAmountToMinor,
} from './money'

describe('parseAmountToMinor', () => {
  it('parses plain integers', () => {
    expect(parseAmountToMinor('12')).toBe(1200)
    expect(parseAmountToMinor('0')).toBe(0)
  })

  it('parses en-US formatted amounts', () => {
    expect(parseAmountToMinor('1,234.56')).toBe(123456)
    expect(parseAmountToMinor('$1,000')).toBe(100000)
    expect(parseAmountToMinor('9.99')).toBe(999)
  })

  it('parses comma-decimal amounts', () => {
    expect(parseAmountToMinor('12,34')).toBe(1234)
    expect(parseAmountToMinor('0,5')).toBe(50)
  })

  it('treats grouped thousands as grouping', () => {
    expect(parseAmountToMinor('1,234')).toBe(123400)
    expect(parseAmountToMinor('1.234.567')).toBe(123456700)
  })

  it('parses negatives and ignores currency symbols', () => {
    expect(parseAmountToMinor('-€ 12,50')).toBe(-1250)
    expect(parseAmountToMinor('+3.25')).toBe(325)
  })

  it('honours zero-decimal currencies', () => {
    expect(parseAmountToMinor('1,234', 0)).toBe(1234)
    expect(parseAmountToMinor('500', 0)).toBe(500)
  })

  it('returns null for garbage', () => {
    expect(parseAmountToMinor('')).toBeNull()
    expect(parseAmountToMinor('abc')).toBeNull()
    expect(parseAmountToMinor('-')).toBeNull()
  })
})

describe('formatMinor', () => {
  it('formats minor units as decimals', () => {
    expect(formatMinor(123456)).toBe('1234.56')
    expect(formatMinor(5)).toBe('0.05')
    expect(formatMinor(-5)).toBe('-0.05')
    expect(formatMinor(0)).toBe('0.00')
  })

  it('formats zero-decimal currencies without a separator', () => {
    expect(formatMinor(1234, 0)).toBe('1234')
  })

  it('handles nullish input', () => {
    expect(formatMinor(null)).toBe('0')
    expect(formatMinor(undefined)).toBe('0')
  })

  it('round-trips with parseAmountToMinor', () => {
    for (const value of ['1234.56', '0.07', '-99.99', '1000']) {
      expect(formatMinor(parseAmountToMinor(value))).toBe(
        value.startsWith('-') ? value : value.includes('.') ? value : `${value}.00`,
      )
    }
  })
})

describe('fractionDigitsFor', () => {
  it('uses the reference table first', () => {
    expect(fractionDigitsFor('USD', { JPY: 0 })).toBe(2)
    expect(fractionDigitsFor('JPY', { JPY: 0 })).toBe(0)
  })

  it('falls back to known currency digits', () => {
    expect(fractionDigitsFor('JPY')).toBe(0)
    expect(fractionDigitsFor('KWD')).toBe(3)
    expect(fractionDigitsFor('EUR')).toBe(2)
  })
})

describe('decimalToMinor', () => {
  it('throws on non-numeric input', () => {
    expect(() => decimalToMinor('nope')).toThrow(TypeError)
  })
})

describe('formatMoney', () => {
  it('renders a localised currency string', () => {
    expect(formatMoney(123456, 'USD', 2, 'en-US')).toBe('$1,234.56')
    expect(formatMoney(5, 'EUR', 2, 'en-US')).toBe('€0.05')
    expect(formatMoney(-1234, 'USD', 2, 'en-US')).toBe('−$12.34')
  })

  it('returns an em dash for nullish input and falls back on bad codes', () => {
    expect(formatMoney(null, 'USD')).toBe('—')
    expect(formatMoney(100, 'NOT-A-CODE', 2, 'en-US')).toBe('1.00')
  })

  it('infers zero-decimal digit counts from the currency code', () => {
    expect(formatMoney(1234, 'JPY', undefined, 'en-US')).toBe('¥1,234')
  })
})

describe('convertMinor', () => {
  it('scales across differing fraction digits', () => {
    expect(convertMinor(100_00, 0.01, 0, 2)).toBe(1)
    expect(convertMinor(100_00, 150, 2, 2)).toBe(1_500_000)
    expect(convertMinor(100_00, 150, 0, 2)).toBe(15_000)
  })

  it('returns 0 for unusable rates', () => {
    expect(convertMinor(100, 0)).toBe(0)
    expect(convertMinor(100, Number.NaN)).toBe(0)
  })
})
