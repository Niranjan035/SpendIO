import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './ApiError'
import { buildUrl, http, request } from './apiClient'
import { clearAccessToken } from './tokenManager'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('buildUrl', () => {
  it('prefixes the API base and serialises query params', () => {
    expect(buildUrl('/accounts')).toBe('/api/v1/accounts')
    expect(buildUrl('/accounts', { page: 0, size: 25 })).toBe('/api/v1/accounts?page=0&size=25')
  })

  it('drops empty values and expands arrays', () => {
    const url = buildUrl('/transactions', {
      q: '',
      type: undefined,
      categories: ['food', 'rent'],
      archived: false,
    })
    expect(url).toBe('/api/v1/transactions?categories=food&categories=rent&archived=false')
  })
})

function callArg(fetchMock: { mock: { calls: unknown[][] } }, index: number, arg: number): unknown {
  const call = fetchMock.mock.calls[index]
  if (!call) throw new Error(`expected call #${index}`)
  return call[arg]
}

function headersAt(
  fetchMock: { mock: { calls: unknown[][] } },
  index: number,
): Record<string, string> {
  const init = callArg(fetchMock, index, 1) as RequestInit | undefined
  return (init?.headers ?? {}) as Record<string, string>
}

describe('request', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns parsed JSON on success', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'a1' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(http.get('/accounts/a1')).resolves.toEqual({ id: 'a1' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(callArg(fetchMock, 0, 0)).toBe('/api/v1/accounts/a1')
    expect(headersAt(fetchMock, 0).Authorization).toBeUndefined()
  })

  it('sends the bearer token when one is held', async () => {
    const { setAccessToken } = await import('./tokenManager')
    setAccessToken('token-123', Date.now() + 60_000)
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)

    await http.get('/users/me')

    expect(headersAt(fetchMock, 0).Authorization).toBe('Bearer token-123')
  })

  it('throws a typed ApiError with field errors', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        {
          status: 400,
          code: 'VALIDATION_ERROR',
          detail: 'Request validation failed',
          fieldErrors: [{ field: 'email', message: 'Enter a valid email' }],
          traceId: 'abc123',
        },
        400,
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    const error = await request('/auth/register', { method: 'POST', body: {} }).catch(
      (e: unknown) => e,
    )
    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.code).toBe('VALIDATION_ERROR')
    expect(apiError.fieldError('email')).toBe('Enter a valid email')
    expect(apiError.isValidation).toBe(true)
    expect(apiError.traceId).toBe('abc123')
  })

  it('refreshes once on 401 and replays the original request', async () => {
    const { setAccessToken } = await import('./tokenManager')
    setAccessToken('stale', Date.now() + 60_000)

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(
        jsonResponse({ accessToken: 'fresh-token', expiresIn: 900, user: { id: 'u1' } }),
      )
      .mockResolvedValueOnce(jsonResponse({ id: 'a1' }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(http.get('/accounts/a1')).resolves.toEqual({ id: 'a1' })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(callArg(fetchMock, 1, 0)).toContain('/auth/refresh')
    expect(headersAt(fetchMock, 2).Authorization).toBe('Bearer fresh-token')
  })

  it('fails the request when the refresh is rejected', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response('', { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    const error = await http.get('/accounts').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not attempt a refresh for skipAuth requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('', { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      request('/auth/login', { method: 'POST', skipAuth: true, body: {} }),
    ).rejects.toBeInstanceOf(ApiError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('resolves to undefined for 204 responses', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(http.delete('/notifications/n1')).resolves.toBeUndefined()
  })
})
