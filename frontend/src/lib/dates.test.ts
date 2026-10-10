import { describe, expect, it } from 'vitest'
import { formatBusinessDate, isWithin, lastNDaysRange, monthKey, parseApiDate } from './dates'

describe('parseApiDate', () => {
  it('parses a plain business date as local midnight', () => {
    const date = parseApiDate('2026-03-12')
    expect(date).not.toBeNull()
    expect(date!.getFullYear()).toBe(2026)
    expect(date!.getMonth()).toBe(2)
    expect(date!.getDate()).toBe(12)
  })

  it('parses full instants', () => {
    expect(parseApiDate('2026-03-12T10:30:00Z')).not.toBeNull()
  })

  it('returns null for empty or invalid input', () => {
    expect(parseApiDate('')).toBeNull()
    expect(parseApiDate(null)).toBeNull()
    expect(parseApiDate('not-a-date')).toBeNull()
  })
})

describe('formatBusinessDate', () => {
  it('formats ISO dates for display', () => {
    expect(formatBusinessDate('2026-03-12')).toBe('12 Mar 2026')
  })

  it('shows an em dash when there is no date', () => {
    expect(formatBusinessDate(undefined)).toBe('—')
  })
})

describe('monthKey', () => {
  it('produces a sortable yyyy-MM key', () => {
    expect(monthKey(new Date(Date.UTC(2026, 0, 31)))).toBe('2026-01')
  })
})

describe('lastNDaysRange', () => {
  it('returns an inclusive range ending today', () => {
    const range = lastNDaysRange(7)
    expect(range.to).toHaveLength(10)
    expect(range.from).toHaveLength(10)
    expect(range.from <= range.to).toBe(true)
    const days = (Date.parse(range.to) - Date.parse(range.from)) / 86_400_000
    expect(days).toBe(6)
  })
})

describe('isWithin', () => {
  it('compares ISO dates lexically and inclusively', () => {
    expect(isWithin('2026-03-12', '2026-03-01', '2026-03-31')).toBe(true)
    expect(isWithin('2026-03-01', '2026-03-01', '2026-03-31')).toBe(true)
    expect(isWithin('2026-04-01', '2026-03-01', '2026-03-31')).toBe(false)
  })
})
