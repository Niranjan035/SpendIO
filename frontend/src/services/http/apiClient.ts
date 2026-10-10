import { API_BASE } from '@/lib/constants'
import { ApiError, toApiError } from './ApiError'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenManager'
import { endpoints } from './endpoints'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type QueryValue =
  string | number | boolean | undefined | null | ReadonlyArray<string | number>

export interface RequestOptions {
  method?: HttpMethod
  body?: unknown
  formData?: FormData
  query?: Record<string, QueryValue>
  headers?: Record<string, string>
  signal?: AbortSignal
  /** Skip the Authorization header (login, register, refresh). */
  skipAuth?: boolean
  /** Send the double-submit CSRF header (cookie-backed endpoints only). */
  requireCsrf?: boolean
}

const NO_BODY_METHODS: ReadonlyArray<HttpMethod> = ['GET']

/** Single-flight refresh: concurrent 401s share one refresh round-trip. */
let refreshInFlight: Promise<boolean> | null = null

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const base = path.startsWith('http')
    ? path
    : `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
  if (!query) return base
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value)) {
      for (const item of value) params.append(key, String(item))
    } else {
      params.append(key, String(value))
    }
  }
  const qs = params.toString()
  return qs ? `${base}?${qs}` : base
}

export function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match?.[1] ? decodeURIComponent(match[1]) : null
}

function xsrfHeader(): Record<string, string> {
  const token = readCookie('XSRF-TOKEN')
  return token ? { 'X-XSRF-TOKEN': token } : {}
}

async function performRefresh(): Promise<boolean> {
  try {
    const response = await fetch(buildUrl(endpoints.auth.refresh), {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
    })
    if (!response.ok) {
      clearAccessToken()
      return false
    }
    const payload = (await response.json()) as { accessToken?: string; expiresIn?: number }
    if (!payload.accessToken) {
      clearAccessToken()
      return false
    }
    const ttlMs = typeof payload.expiresIn === 'number' ? payload.expiresIn * 1000 : 0
    setAccessToken(payload.accessToken, ttlMs > 0 ? Date.now() + ttlMs : 0)
    return true
  } catch {
    clearAccessToken()
    return false
  }
}

export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

function notifySessionExpired(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('spendio:session-expired'))
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, formData, query, headers = {}, signal } = options
  const skipAuth = options.skipAuth === true
  const url = buildUrl(path, query)

  const send = async (): Promise<Response> => {
    const finalHeaders: Record<string, string> = { Accept: 'application/json', ...headers }
    if (!skipAuth) {
      const token = getAccessToken()
      if (token) finalHeaders.Authorization = `Bearer ${token}`
    }
    if (options.requireCsrf) Object.assign(finalHeaders, xsrfHeader())

    let payload: BodyInit | undefined
    if (formData) {
      payload = formData
    } else if (body !== undefined && !NO_BODY_METHODS.includes(method)) {
      finalHeaders['Content-Type'] = 'application/json'
      payload = JSON.stringify(body)
    }

    return fetch(url, {
      method,
      headers: finalHeaders,
      body: payload,
      credentials: options.requireCsrf ? 'include' : 'same-origin',
      signal,
    })
  }

  let response = await send()

  const retriable = response.status === 401 && !skipAuth && !path.startsWith(endpoints.auth.refresh)
  if (retriable) {
    const refreshed = await refreshSession()
    if (refreshed) {
      response = await send()
    } else {
      notifySessionExpired()
    }
  }

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }

  const text = await response.text()
  if (!text) return undefined as T
  try {
    return JSON.parse(text) as T
  } catch {
    throw new ApiError(response.status, null, 'Malformed response')
  }
}

export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body' | 'formData'>) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'formData'>) =>
    request<T>(path, { ...options, method: 'DELETE' }),
}
