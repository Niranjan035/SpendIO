export const DEFAULT_BASE_CURRENCY = 'USD'

export function formatCurrencyMinor(minor: number, code: string = DEFAULT_BASE_CURRENCY): string {
  const abs = Math.abs(minor)
  const sign = minor < 0 ? '-' : ''
  const intPart = Math.floor(abs / 100)
  const frac = abs % 100
  return `${sign}${intPart.toLocaleString()}.${frac.toString().padStart(2, '0')} ${code}`
}

/** Renders a resolved FX rate for display (up to 6 decimals, no exponent noise). */
export function formatFxRate(rate: number): string {
  if (!Number.isFinite(rate) || rate <= 0) return '-'
  return rate.toLocaleString('en-GB', { maximumFractionDigits: 6 })
}
