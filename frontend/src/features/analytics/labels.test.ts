import { describe, expect, it } from 'vitest'
import { lastNDaysRange, toIsoDate } from '@/lib/dates'
import { BUDGET_UTILIZATION_LABELS, compactMoney, formatDeltaPct, monthBucketLabel, presetRange, RANGE_PRESETS } from './labels'

describe('analytics labels', () => {
  it('renders month buckets as human labels', () => {
    expect(monthBucketLabel('2026-10')).toMatch(/Oct 2026|October 2026/)
    expect(monthBucketLabel('2026-01')).toMatch(/Jan 2026|January 2026/)
  })

  it('formats compact axes and deltas', () => {
    expect(compactMoney(123456)).toBe('1.2K')
    expect(compactMoney(0)).toBe('0')
    expect(formatDeltaPct(12.34)).toBe('+12.3%')
    expect(formatDeltaPct(-4.0)).toBe('-4.0%')
    expect(formatDeltaPct(null)).toBe('—')
    expect(formatDeltaPct(undefined)).toBe('—')
  })

  it('compact axes scale the same major amount across currencies', () => {
    const usdMajor = 12345.0
    expect(compactMoney(usdMajor * 100, 'USD')).toBe(compactMoney(usdMajor, 'JPY'))
    expect(compactMoney(usdMajor * 1000, 'KWD')).toBe(compactMoney(usdMajor, 'JPY'))
    expect(compactMoney(usdMajor * 100, 'INR')).toBe(compactMoney(usdMajor, 'JPY'))
    expect(compactMoney(1234500, undefined)).toBe(compactMoney(1234500, 'USD'))
    expect(compactMoney(null, 'INR')).toBe('—')
  })

  it('maps budget utilisation statuses to readable labels', () => {
    expect(BUDGET_UTILIZATION_LABELS.OVER_BUDGET).toBe('Over budget')
    expect(BUDGET_UTILIZATION_LABELS.AT_RISK).toBe('At risk')
  })

  it('presets are inclusive, end today and stay inside the D.14 caps', () => {
    for (const preset of RANGE_PRESETS) {
      const { from, to } = presetRange(preset)
      const spanDays = (Date.parse(to) - Date.parse(from)) / 86_400_000 + 1
      expect(spanDays).toBe(preset.days)
      expect(spanDays).toBeLessThan(1900)
      expect(to).toBe(toIsoDate(new Date()))
    }
    const covered = new Set(lastNDaysRange(30).from)
    expect(covered).toBeDefined()
  })
})