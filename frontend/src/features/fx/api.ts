import { http } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import { toIsoDate } from '@/lib/dates'
import type {
  ConvertInput,
  ConvertResult,
  FxRate,
  ManualRateInput,
  RateHistoryQuery,
  RefreshSummary,
} from './types'

export const fxApi = {
  /** POST /fx/convert - conversion at the resolved rate (ARCHITECTURE.md D.5). */
  convert(input: ConvertInput): Promise<ConvertResult> {
    return http.post<ConvertResult>(endpoints.fx.convert, input)
  },

  /** GET /fx/rates/current - resolved rate through the documented fallback chain. */
  currentRate(from: string, to: string, date?: string): Promise<FxRate> {
    return http.get<FxRate>(endpoints.fx.current, { query: { from, to, date } })
  },

  /** GET /fx/rates - newest-first history for a pair. */
  history(query: RateHistoryQuery): Promise<FxRate[]> {
    return http.get<FxRate[]>(endpoints.fx.rates, { query: { ...query } })
  },

  /** POST /fx/rates - manual override; always preferred over provider rows. */
  overrideRate(input: ManualRateInput): Promise<FxRate> {
    return http.post<FxRate>(endpoints.fx.rates, input)
  },

  /** POST /fx/refresh - run the daily refresh job now. */
  refresh(): Promise<RefreshSummary> {
    return http.post<RefreshSummary>(endpoints.fx.refresh)
  },
}

/** Today's date as an ISO `yyyy-MM-dd` string - the default pricing date. */
export function todayIsoDate(): string {
  return toIsoDate(new Date())
}
