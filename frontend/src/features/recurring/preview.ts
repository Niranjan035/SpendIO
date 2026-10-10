import {
  addDays,
  addMonths,
  addYears,
  getDate,
  getDay,
  getDaysInMonth,
  getMonth,
  getYear,
  startOfMonth,
  startOfWeek,
} from 'date-fns'
import { parseApiDate, toIsoDate } from '@/lib/dates'
import type { RecurrenceFrequency } from '@/types/enums'

/**
 * Client mirror of the backend {@code RecurrenceScheduleCalculator} (ARCHITECTURE.md D.9):
 * the next {@code count} occurrence dates of a rule, used to preview the schedule in the
 * create form before anything is saved. It reproduces the same anchor/index arithmetic
 * and the same weekend-shift advance, so unsaved previews match what the server will
 * generate.
 */
export interface PreviewRule {
  startDate: string
  endDate: string | null
  frequency: RecurrenceFrequency
  intervalCount: number
  dayOfMonth: number | null
  dayOfWeek: number | null
  skipWeekends: boolean
  targetOccurrenceDate: string | null
}

function toDate(value: string): Date {
  return parseApiDate(value) ?? new Date()
}

/** ISO-8601 day number (1 = Monday .. 7 = Sunday), matching the backend's dayOfWeek. */
function isoWeekDay(date: Date): number {
  const day = getDay(date)
  return day === 0 ? 7 : day
}

function shiftWeekend(date: Date): Date {
  const day = isoWeekDay(date)
  if (day === 6) return addDays(date, -1)
  if (day === 7) return addDays(date, 1)
  return date
}

function floorDiv(dividend: number, divisor: number): number {
  return Math.floor(dividend / divisor)
}

function weeklyAnchor(rule: PreviewRule): Date {
  const start = toDate(rule.startDate)
  const weekStart = startOfWeek(start, { weekStartsOn: 1 })
  const day = rule.dayOfWeek ?? isoWeekDay(start)
  return addDays(weekStart, day - 1)
}

function occurrence(rule: PreviewRule, k: number): Date {
  const start = toDate(rule.startDate)
  const interval = Math.max(1, rule.intervalCount)
  switch (rule.frequency) {
    case 'DAILY':
      return addDays(start, k * interval)
    case 'WEEKLY':
      return addDays(weeklyAnchor(rule), k * 7 * interval)
    case 'MONTHLY': {
      const firstOfMonth = startOfMonth(addMonths(start, k * interval))
      const day = Math.min(rule.dayOfMonth ?? getDate(start), getDaysInMonth(firstOfMonth))
      return addDays(firstOfMonth, day - 1)
    }
    case 'YEARLY':
      return addYears(start, k * interval)
    case 'CUSTOM':
      return addDays(toDate(rule.targetOccurrenceDate ?? rule.startDate), k * interval)
  }
}

function lowerBound(rule: PreviewRule, from: Date): number {
  const interval = Math.max(1, rule.intervalCount)
  const start = toDate(rule.startDate)
  switch (rule.frequency) {
    case 'DAILY':
      return Math.max(0, floorDiv(daysBetween(start, from), interval))
    case 'WEEKLY':
      return Math.max(0, floorDiv(daysBetween(weeklyAnchor(rule), from), 7 * interval))
    case 'CUSTOM':
      return Math.max(
        0,
        floorDiv(daysBetween(toDate(rule.targetOccurrenceDate ?? rule.startDate), from), interval),
      )
    case 'MONTHLY': {
      const months =
        12 * (getYear(from) - getYear(start)) + (getMonth(from) - getMonth(start))
      return Math.max(0, floorDiv(months, interval) - 1)
    }
    case 'YEARLY':
      return Math.max(0, floorDiv(getYear(from) - getYear(start), interval) - 1)
  }
}

function daysBetween(from: Date, to: Date): number {
  const ms = Date.UTC(to.getFullYear(), to.getMonth(), to.getDate()) - Date.UTC(from.getFullYear(), from.getMonth(), from.getDate())
  return ms / 86_400_000
}

function firstOnOrAfter(rule: PreviewRule, from: Date): Date | null {
  const start = toDate(rule.startDate)
  const endDate = rule.endDate ? toDate(rule.endDate) : null
  let k = lowerBound(rule, from)
  let candidate = occurrence(rule, k)
  let steps = 0
  while ((candidate < from || candidate < start) && steps++ < 10_000) {
    candidate = occurrence(rule, ++k)
  }
  if (candidate < from || candidate < start) return null
  if (endDate !== null && candidate > endDate) return null
  if (rule.skipWeekends) {
    const shifted = shiftWeekend(candidate)
    const outside = shifted < from || shifted < start || (endDate !== null && shifted > endDate)
    if (outside) return firstOnOrAfter(rule, addDays(candidate, 1))
    return shifted
  }
  return candidate
}

export function previewDates(
  rule: PreviewRule,
  from: string,
  count: number,
): string[] {
  const wanted = Math.max(0, Math.min(count, 31))
  const dates: string[] = []
  let cursor = toDate(from)
  let guard = 0
  while (dates.length < wanted && guard++ < 10_000) {
    const candidate = firstOnOrAfter(rule, cursor)
    if (candidate === null) break
    dates.push(toIsoDate(candidate))
    cursor = addDays(candidate, 1)
  }
  return dates
}