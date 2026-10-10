/** Application-wide constants. */

export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/** Hard caps mirrored from the backend contract. */
export const MAX_PAGE_SIZE = 100
export const DEFAULT_PAGE_SIZE = 25
export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const

export const SEARCH_DEBOUNCE_MS = 300

export const ACCESS_TOKEN_KEY = 'spendio.accessToken'
export const THEME_STORAGE_KEY = 'spendio.theme'
export const LOCALE_STORAGE_KEY = 'spendio.locale'

export const SUPPORTED_CURRENCIES = [
  'USD',
  'EUR',
  'GBP',
  'JPY',
  'CHF',
  'CAD',
  'AUD',
  'NZD',
  'SEK',
  'NOK',
  'DKK',
  'INR',
  'SGD',
  'HKD',
  'ZAR',
  'BRL',
  'MXN',
  'PLN',
  'CZK',
  'HUF',
] as const
