import { useEffect, useState } from 'react'
import { fxApi, todayIsoDate } from '@/features/fx/api'
import type { ConvertResult } from '@/features/fx/types'
import { SEARCH_DEBOUNCE_MS } from '@/lib/constants'
import { formatFxRate } from '@/lib/currency'
import { formatMoney, fractionDigitsFor, parseAmountToMinor } from '@/lib/money'
import { Input, type InputProps } from './Input'

export interface MoneyInputProps extends Omit<InputProps, 'value' | 'onChange' | 'numeric'> {
  /** Currency the amount is typed in. */
  currency: string
  /** Currency the converted value is displayed in (the user's base currency). */
  baseCurrency: string
  /** Raw user text - the parent owns it so parsing stays in one place. */
  value: string
  onChange: (value: string) => void
  /** Minor-unit digits of `currency`; defaults to the currency's own. */
  fractionDigits?: number
}

/**
 * Amount field that shows what the entered amount is worth in the base currency.
 * The conversion is debounced and priced today through `POST /fx/convert`
 * (ARCHITECTURE.md D.5); when the amount is already in the base currency the
 * hint is unnecessary, so no request is made at all.
 */
export function MoneyInput(props: MoneyInputProps) {
  const { currency, baseCurrency, value, onChange, fractionDigits, hint, ...inputProps } = props
  const digits = fractionDigits ?? fractionDigitsFor(currency, {})
  const identity = currency.trim().toUpperCase() === baseCurrency.trim().toUpperCase()
  const minor = identity ? null : parseAmountToMinor(value, digits)
  const from = currency.trim().toUpperCase()
  const base = baseCurrency.trim().toUpperCase()
  /** Identifies the amount a conversion belongs to - results for older amounts are ignored. */
  const requestKey = minor === null ? null : `${from}/${base}/${minor}`
  const [conversion, setConversion] = useState<{ key: string; value: ConvertResult } | null>(null)

  useEffect(() => {
    if (requestKey === null || minor === null) return
    let cancelled = false
    const timer = window.setTimeout(() => {
      fxApi
        .convert({
          fromCurrency: from,
          toCurrency: base,
          amountMinor: minor,
          date: todayIsoDate(),
        })
        .then((result) => {
          if (!cancelled) setConversion({ key: requestKey, value: result })
        })
        .catch(() => {
          if (!cancelled) setConversion(null)
        })
    }, SEARCH_DEBOUNCE_MS)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [requestKey, minor, from, base])

  const active = conversion && conversion.key === requestKey ? conversion.value : null
  const conversionLine = active
    ? [
        `≈ ${formatMoney(active.toAmountMinor, baseCurrency)}`,
        `1 ${from} = ${formatFxRate(active.rate)} ${base}`,
        active.stale ? `stale rate from ${active.rateDate}` : null,
      ]
        .filter(Boolean)
        .join(' · ')
    : null
  const combinedHint = [hint, conversionLine].filter(Boolean).join('  ·  ')

  return (
    <Input
      {...inputProps}
      numeric
      inputMode="decimal"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      hint={combinedHint === '' ? undefined : combinedHint}
    />
  )
}
