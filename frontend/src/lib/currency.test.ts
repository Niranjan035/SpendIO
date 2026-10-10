import { describe, expect, it } from 'vitest'
import { DEFAULT_BASE_CURRENCY, formatCurrencyMinor, formatFxRate } from './currency'

describe('formatCurrencyMinor', () => {
  it('formats minor units with the code appended', () => {
    expect(formatCurrencyMinor(123456)).toBe('1,234.56 USD')
    expect(formatCurrencyMinor(-50, 'EUR')).toBe('-0.50 EUR')
    expect(formatCurrencyMinor(0)).toBe('0.00 USD')
  })

  it('defaults to the application base currency', () => {
    expect(formatCurrencyMinor(1)).toBe(`0.01 ${DEFAULT_BASE_CURRENCY}`)
  })
})

describe('formatFxRate', () => {
  it('renders six decimals without exponent notation', () => {
    expect(formatFxRate(1.0800649)).toBe('1.080065')
    expect(formatFxRate(150.5)).toBe('150.5')
  })

  it('renders a dash for rates that cannot be displayed', () => {
    expect(formatFxRate(0)).toBe('-')
    expect(formatFxRate(-1)).toBe('-')
    expect(formatFxRate(Number.NaN)).toBe('-')
  })
})
