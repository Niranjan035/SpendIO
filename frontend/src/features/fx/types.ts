/** FX DTOs mirroring the backend contracts (ARCHITECTURE.md D.5). */

/** GET /fx/rates and GET /fx/rates/current */
export interface FxRate {
  baseCurrencyCode: string
  quoteCurrencyCode: string
  rate: number
  rateDate: string
  /** Resolution path: "manual", "provider", "inverse(...)", "triangulated", "fetch". */
  source: string
}

/** POST /fx/convert - amounts are integer minor units (ADR-0002). */
export interface ConvertInput {
  fromCurrency: string
  toCurrency: string
  amountMinor: number
  date: string
}

export interface ConvertResult {
  fromAmountMinor: number
  toAmountMinor: number
  rate: number
  rateDate: string
  source: string
  /** True when the fallback chain had to use a rate older than the freshness window. */
  stale: boolean
}

/** POST /fx/rates - manual override, always preferred on read. */
export interface ManualRateInput {
  baseCurrencyCode: string
  quoteCurrencyCode: string
  rate: number
  rateDate: string
}

/** GET /fx/rates query. */
export interface RateHistoryQuery {
  base: string
  quote: string
  from?: string
  to?: string
}

/** POST /fx/refresh - result of running the daily job now. */
export interface RefreshSummary {
  job: string
  rateDate: string
  provider: string
  rows: number
  status: string
}
