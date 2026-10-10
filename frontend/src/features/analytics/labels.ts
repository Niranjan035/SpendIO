import { lastNDaysRange } from '@/lib/dates'
import { fractionDigitsFor } from '@/lib/money'
import type { AnalyticsGranularity } from './types'

export const GRANULARITY_OPTIONS: ReadonlyArray<{ value: AnalyticsGranularity; label: string }> = [
  { value: 'daily', label: 'Daily' },
  { value: 'weekly', label: 'Weekly' },
  { value: 'monthly', label: 'Monthly' },
]

export interface RangePreset {
  label: string
  days: number
  granularity: AnalyticsGranularity
}

/** Presets stay inside the backend granularity caps (D.14 validator). */
export const RANGE_PRESETS: ReadonlyArray<RangePreset> = [
  { label: '30 days', days: 30, granularity: 'daily' },
  { label: '90 days', days: 92, granularity: 'weekly' },
  { label: '12 months', days: 365, granularity: 'monthly' },
]

/** Inclusive {from,to} for a preset, ending today. */
export function presetRange(preset: RangePreset): { from: string; to: string } {
  return lastNDaysRange(preset.days)
}

export const BUDGET_UTILIZATION_LABELS: Record<
  'ON_TRACK' | 'AT_RISK' | 'OVER_BUDGET',
  string
> = {
  ON_TRACK: 'On track',
  AT_RISK: 'At risk',
  OVER_BUDGET: 'Over budget',
}

export const ACCOUNT_TYPE_LABELS: Record<string, string> = {
  CASH: 'Cash',
  BANK: 'Bank',
  CREDIT_CARD: 'Credit card',
  WALLET: 'Wallet',
  INVESTMENT: 'Investment',
  LOAN: 'Loan',
}

const COMPACT = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 })

/**
 * Compact money for chart axes ("1.2K", "340"). Major units, no symbol. The
 * divisor follows the currency's own fraction digits so JPY/KWD-style axes are
 * not thrown off; defaulting to USD keeps the no-currency call site behaviour.
 */
export function compactMoney(minor: number | null | undefined, currency?: string): string {
  if (minor === null || minor === undefined) return '—'
  const major = minor / 10 ** fractionDigitsFor(currency ?? 'USD')
  return COMPACT.format(major)
}

/** +12.5% / -4.0% style delta for the top-spending table; "—" when unknown. */
export function formatDeltaPct(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  const fixed = value.toFixed(1)
  const sign = value > 0 ? '+' : ''
  return `${sign}${fixed}%`
}

/** Human month label for a `yyyy-MM` bucket. */
export function monthBucketLabel(value: string): string {
  const [rawYear, rawMonth] = value.split('-')
  const year = Number(rawYear)
  const month = Number(rawMonth)
  if (!Number.isFinite(year) || !Number.isFinite(month)) return value
  const monthName = new Intl.DateTimeFormat(undefined, { month: 'short' }).format(
    new Date(year, month - 1, 1),
  )
  return `${monthName} ${year}`
}

/** Tolerant bucket label for Recharts tooltips (labels arrive as ReactNode). */
export function bucketLabel(value: unknown): string {
  return monthBucketLabel(typeof value === 'string' ? value : String(value))
}