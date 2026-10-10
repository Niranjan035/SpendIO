import { afterEach, describe, expect, it, vi } from 'vitest'
import { authApi, toSessionUser } from './api'
import type { BackendAuthResponse } from './types'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const backendUser = {
  id: 42,
  email: 'ana@example.com',
  firstName: 'Ana',
  lastName: 'Lopes',
  displayName: 'Ana Lopes',
  timezone: 'Europe/Lisbon',
  locale: 'en-GB',
  baseCurrencyCode: 'EUR',
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

const backendAuth: BackendAuthResponse = {
  accessToken: 'header.payload.signature',
  expiresIn: 900,
  tokenType: 'Bearer',
  user: backendUser,
  permissions: [],
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

describe('toSessionUser', () => {
  it('maps the backend UserResponse onto the store shape', () => {
    expect(toSessionUser(backendUser)).toEqual({
      id: '42',
      email: 'ana@example.com',
      firstName: 'Ana',
      lastName: 'Lopes',
      displayName: 'Ana Lopes',
      baseCurrency: 'EUR',
      timezone: 'Europe/Lisbon',
      locale: 'en-GB',
      emailVerified: true,
      createdAt: '2026-01-01T00:00:00Z',
    })
  })

  it('falls back to the email when the display name is missing', () => {
    const mapped = toSessionUser({ id: 7, email: 'solo@example.com', baseCurrencyCode: 'USD' })
    expect(mapped.displayName).toBe('solo@example.com')
    expect(mapped.id).toBe('7')
    expect(mapped.timezone).toBeUndefined()
  })
})

describe('authApi', () => {
  it('posts the register payload and adopts the session', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(backendAuth))
    vi.stubGlobal('fetch', fetchMock)

    const session = await authApi.register({
      email: 'ana@example.com',
      password: 'Str0ngPassphrase1',
      firstName: 'Ana',
      lastName: 'Lopes',
      timezone: 'Europe/Lisbon',
      locale: 'en-GB',
      baseCurrencyCode: 'EUR',
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/auth/register')
    const body = JSON.parse(String(init.body)) as Record<string, unknown>
    expect(body).toMatchObject({
      firstName: 'Ana',
      lastName: 'Lopes',
      timezone: 'Europe/Lisbon',
      locale: 'en-GB',
      baseCurrencyCode: 'EUR',
    })
    expect(body).not.toHaveProperty('displayName')

    expect(session.accessToken).toBe(backendAuth.accessToken)
    expect(session.expiresIn).toBe(900)
    expect(session.user.id).toBe('42')
    expect(session.user.baseCurrency).toBe('EUR')
  })

  it('maps the login response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(backendAuth))
    vi.stubGlobal('fetch', fetchMock)

    const session = await authApi.login({ email: 'ana@example.com', password: 'Str0ngPassphrase1' })
    expect(session.user.email).toBe('ana@example.com')
    expect(session.user.displayName).toBe('Ana Lopes')
  })

  it('maps GET /users/me', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(backendUser))
    vi.stubGlobal('fetch', fetchMock)

    const user = await authApi.me()
    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/users/me')
    expect(user).toMatchObject({ id: '42', baseCurrency: 'EUR' })
  })

  it('sends logout-all with the CSRF double-submit header', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-123'
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await authApi.logoutAll()

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/auth/logout-all')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-123')
  })
})
