/**
 * Money helpers. The wire format is always integer minor units (ADR-0002);
 * these functions are the only place a decimal string may be produced.
 */

export const DEFAULT_FRACTION_DIGITS = 2

/** Digits used for a currency when the reference data does not say otherwise. */
const FALLBACK_DIGITS: Record<string, number> = {
  JPY: 0,
  KRW: 0,
  VND: 0,
  CLP: 0,
  ISK: 0,
  KWD: 3,
  BHD: 3,
  OMR: 3,
  TND: 3,
}

export function fractionDigitsFor(currency: string, table?: Record<string, number>): number {
  const code = currency.toUpperCase()
  if (table && typeof table[code] === 'number') return table[code]
  return FALLBACK_DIGITS[code] ?? DEFAULT_FRACTION_DIGITS
}

/**
 * Parses user-entered text ("1,234.56", "-12", "€ 9,99") into integer minor
 * units, honouring the locale-agnostic rules:
 *
 * - the rightmost `.`/`,` is the decimal separator when it is followed by
 *   between 1 and `fractionDigits` digits;
 * - every other separator is thousands grouping and is dropped.
 *
 * Returns `null` when the input is not a finite number.
 */
export function parseAmountToMinor(
  input: string,
  fractionDigits = DEFAULT_FRACTION_DIGITS,
): number | null {
  if (typeof input !== 'string') return null

  const cleaned = input
    .trim()
    .replace(/[^\d.,\-+]/g, '')
    .replace(/\s/g, '')
  if (cleaned === '' || /^[-+.,]+$/.test(cleaned)) return null

  const negative = cleaned.startsWith('-')
  const body = cleaned.replace(/^[+-]/, '')

  const matches = [...body.matchAll(/[.,]/g)].map((m) => m.index ?? -1)
  let digits: string

  if (matches.length === 0) {
    digits = body
  } else {
    const last = matches[matches.length - 1]!
    const trailing = body.length - last - 1
    const isDecimal = fractionDigits > 0 && trailing >= 1 && trailing <= fractionDigits
    const intPart = body.slice(0, last).replace(/[.,]/g, '')
    const fracPart = body.slice(last + 1)
    digits = isDecimal ? `${intPart}.${fracPart}` : `${intPart}${fracPart}`
  }

  if (!/^\d+(\.\d+)?$/.test(digits)) return null
  const value = Number(digits)
  if (!Number.isFinite(value)) return null
  const minor = Math.round(value * 10 ** fractionDigits)
  return negative && minor !== 0 ? -minor : minor
}

/** Renders minor units as a plain decimal string ("123456" -> "1234.56"). */
export function formatMinor(
  minor: number | null | undefined,
  fractionDigits = DEFAULT_FRACTION_DIGITS,
): string {
  if (minor === null || minor === undefined || !Number.isFinite(minor)) return '0'
  const sign = minor < 0 ? '-' : ''
  const abs = Math.abs(Math.trunc(minor))
  if (fractionDigits <= 0) return `${sign}${abs}`
  const factor = 10 ** fractionDigits
  const whole = Math.floor(abs / factor)
  const rest = String(abs % factor).padStart(fractionDigits, '0')
  return `${sign}${whole}.${rest}`
}

/** Parses a decimal string into minor units, throwing when it is not a number. */
export function decimalToMinor(decimal: string, fractionDigits = DEFAULT_FRACTION_DIGITS): number {
  const parsed = parseAmountToMinor(decimal, fractionDigits)
  if (parsed === null) throw new TypeError(`not a number: ${decimal}`)
  return parsed
}

/**
 * Renders minor units as a localised currency string (123456 minor in USD ->
 * "$1,234.56"). Falls back to a plain decimal when the currency code is not
 * understood by Intl.
 */
export function formatMoney(
  minor: number | null | undefined,
  currency = 'USD',
  fractionDigits?: number,
  locale = Intl.DateTimeFormat().resolvedOptions().locale,
): string {
  if (minor === null || minor === undefined) return '—'
  const code = currency.toUpperCase()
  const inferred = fractionDigitsFor(code, {})
  const digits = fractionDigits ?? inferred
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: code,
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    })
      .format(minor / 10 ** inferred)
      .replace('-', '\u2212')
  } catch {
    return formatMinor(minor, digits)
  }
}

/** Converts a two-currency amount at a rate, rounding half-up in minor units. */
export function convertMinor(
  amountMinor: number,
  rate: number,
  targetFractionDigits = DEFAULT_FRACTION_DIGITS,
  sourceFractionDigits = DEFAULT_FRACTION_DIGITS,
): number {
  if (!Number.isFinite(rate) || rate <= 0) return 0
  const asMajor = amountMinor / 10 ** sourceFractionDigits
  return Math.round(asMajor * rate * 10 ** targetFractionDigits)
}
