import { format, parseISO } from 'date-fns'

/** Parses an ISO-8601 date or date-time; returns `null` when unusable. */
export function parseApiDate(value: string | null | undefined): Date | null {
  if (!value) return null
  try {
    const date = value.length <= 10 ? parseISO(`${value}T00:00:00`) : parseISO(value)
    return Number.isNaN(date.getTime()) ? null : date
  } catch {
    return null
  }
}

/** Formats a business date (YYYY-MM-DD) for display: `12 Mar 2026`. */
export function formatBusinessDate(value: string | null | undefined): string {
  const date = parseApiDate(value)
  return date ? format(date, 'dd MMM yyyy') : '—'
}

/** Month key used for grouping/labels: `2026-03`. */
export function monthKey(date: Date): string {
  return format(date, 'yyyy-MM')
}

/** Inclusive ISO date range for the last `days` days, ending today. */
export function lastNDaysRange(days: number): { from: string; to: string } {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - (days - 1))
  return { from: toIsoDate(from), to: toIsoDate(to) }
}

/** Local business day of `date` as YYYY-MM-DD (pairs with {@link parseApiDate}). */
export function toIsoDate(date: Date): string {
  return format(date, 'yyyy-MM-dd')
}

/** True when `value` (YYYY-MM-DD) lies inside `[from, to]`. */
export function isWithin(value: string, from: string, to: string): boolean {
  return value >= from && value <= to
}
