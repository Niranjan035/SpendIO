import { describe, expect, it } from 'vitest'
import { previewDates, type PreviewRule } from './preview'

function rule(overrides: Partial<PreviewRule> = {}): PreviewRule {
  return {
    startDate: '2026-03-01',
    endDate: null,
    frequency: 'DAILY',
    intervalCount: 1,
    dayOfMonth: null,
    dayOfWeek: null,
    skipWeekends: false,
    targetOccurrenceDate: null,
    ...overrides,
  }
}

describe('previewDates', () => {
  it('yields consecutive days for a daily rule', () => {
    expect(previewDates(rule(), '2026-03-01', 5)).toEqual([
      '2026-03-01',
      '2026-03-02',
      '2026-03-03',
      '2026-03-04',
      '2026-03-05',
    ])
  })

  it('anchors weekly rules on the chosen day of the week', () => {
    expect(
      previewDates(rule({ frequency: 'WEEKLY', dayOfWeek: 5 }), '2026-03-02', 5),
    ).toEqual(['2026-03-06', '2026-03-13', '2026-03-20', '2026-03-27', '2026-04-03'])
  })

  it('defaults weekly day to the start date weekday', () => {
    expect(
      previewDates(rule({ frequency: 'WEEKLY', startDate: '2026-03-04' }), '2026-03-04', 3),
    ).toEqual(['2026-03-04', '2026-03-11', '2026-03-18'])
  })

  it('clamps monthly day-of-month to the target month length', () => {
    expect(
      previewDates(
        rule({ frequency: 'MONTHLY', startDate: '2026-01-31', dayOfMonth: 31 }),
        '2026-01-31',
        5,
      ),
    ).toEqual(['2026-01-31', '2026-02-28', '2026-03-31', '2026-04-30', '2026-05-31'])
  })

  it('skips ahead to the next window when asked from mid-period', () => {
    expect(
      previewDates(
        rule({ frequency: 'MONTHLY', startDate: '2026-01-15', dayOfMonth: 15 }),
        '2026-02-01',
        5,
      ),
    ).toEqual(['2026-02-15', '2026-03-15', '2026-04-15', '2026-05-15', '2026-06-15'])
  })

  it('respects the interval count for infrequent rules', () => {
    expect(
      previewDates(
        rule({ frequency: 'MONTHLY', startDate: '2026-01-15', dayOfMonth: 15, intervalCount: 2 }),
        '2026-01-15',
        4,
      ),
    ).toEqual(['2026-01-15', '2026-03-15', '2026-05-15', '2026-07-15'])
  })

  it('clamps leap-day yearly anchors', () => {
    expect(
      previewDates(
        rule({ frequency: 'YEARLY', startDate: '2024-02-29' }),
        '2024-02-29',
        3,
      ),
    ).toEqual(['2024-02-29', '2025-02-28', '2026-02-28'])
  })

  it('steps custom rules from the anchor payday', () => {
    expect(
      previewDates(
        rule({ frequency: 'CUSTOM', targetOccurrenceDate: '2026-03-15', intervalCount: 14 }),
        '2026-03-01',
        4,
      ),
    ).toEqual(['2026-03-15', '2026-03-29', '2026-04-12', '2026-04-26'])
  })

  it('shifts weekend occurrences without producing duplicates', () => {
    const dailySkipWeekends = rule({
      startDate: '2026-03-06',
      skipWeekends: true,
    })
    expect(previewDates(dailySkipWeekends, '2026-03-06', 5)).toEqual([
      '2026-03-06',
      '2026-03-09',
      '2026-03-10',
      '2026-03-11',
      '2026-03-12',
    ])
  })

  it('shifts a Saturday weekly rule to the preceding Friday each week', () => {
    const saturdaySkip = rule({ frequency: 'WEEKLY', dayOfWeek: 6, skipWeekends: true })
    expect(previewDates(saturdaySkip, '2026-03-02', 3)).toEqual([
      '2026-03-06',
      '2026-03-13',
      '2026-03-20',
    ])
  })

  it('stops producing past the end date', () => {
    const bounded = rule({
      frequency: 'WEEKLY',
      dayOfWeek: 1,
      endDate: '2026-03-20',
    })
    expect(previewDates(bounded, '2026-03-01', 10)).toEqual([
      '2026-03-02',
      '2026-03-09',
      '2026-03-16',
    ])
  })

  it('returns the same first window for a weekly rule repeated from its due day', () => {
    const fridays = rule({ frequency: 'WEEKLY', dayOfWeek: 5 })
    const fromSaturday = previewDates(fridays, '2026-03-07', 3)
    const fromFriday = previewDates(fridays, '2026-03-13', 3)
    expect(fromSaturday[0]).toBe('2026-03-13')
    expect(fromFriday[0]).toBe('2026-03-13')
    expect(fromSaturday).toEqual(fromFriday)
  })
})