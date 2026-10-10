import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { accountsApi, buildAccountQuery } from './api'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function call(
  fetchMock: { mock: { calls: unknown[][] } },
  index = 0,
): [string, RequestInit | undefined] {
  const args = fetchMock.mock.calls[index] as [string, RequestInit | undefined]
  return args
}

describe('buildAccountQuery', () => {
  it('includes sane defaults for the list endpoint', () => {
    expect(buildAccountQuery({})).toEqual({
      type: '',
      status: '',
      currencyCode: '',
      q: '',
      includeArchived: false,
      hasTransactions: false,
      sort: '',
      page: 0,
      size: 50,
    })
  })
})

describe('accountsApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists accounts with the serialised filter', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        content: [],
        page: 0,
        size: 25,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const page = await accountsApi.list({
      type: 'BANK',
      status: 'ACTIVE',
      q: 'chase',
      page: 1,
      size: 25,
    })
    expect(page.content).toEqual([])
    const [url] = call(fetchMock)
    expect(url).toBe(
      '/api/v1/accounts?type=BANK&status=ACTIVE&q=chase&includeArchived=false&hasTransactions=false&page=1&size=25',
    )
  })

  it('creates an account via POST /accounts', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 9, name: 'Main' }))
    vi.stubGlobal('fetch', fetchMock)

    await accountsApi.create({
      name: 'Main',
      type: 'BANK',
      currencyCode: 'USD',
      openingBalanceMinor: 1000,
      openingBalanceDate: null,
      institution: null,
      last4: null,
      creditLimitMinor: null,
      billingDay: null,
      dueDay: null,
      icon: null,
      color: null,
      notes: null,
      primary: true,
      openingTransactionDate: null,
      createOpeningBalanceTransaction: false,
    })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/accounts')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toMatchObject({
      name: 'Main',
      type: 'BANK',
      primary: true,
    })
  })

  it('reads totals from GET /accounts/summary', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse({ totalBaseMinor: 5000, baseCurrencyCode: 'USD', byType: {}, byCurrency: {} }),
      )
    vi.stubGlobal('fetch', fetchMock)

    const totals = await accountsApi.summary()
    expect(totals.totalBaseMinor).toBe(5000)
    const [url] = call(fetchMock)
    expect(url).toBe('/api/v1/accounts/summary')
  })

  it('archives, restores and set-primary target /accounts/{id}/...', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ id: 7 }))
      .mockResolvedValueOnce(jsonResponse({ id: 7 }))
      .mockResolvedValueOnce(jsonResponse({ id: 7 }))
    vi.stubGlobal('fetch', fetchMock)

    await accountsApi.archive(7)
    await accountsApi.restore(7)
    await accountsApi.setPrimary(7)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/accounts/7/archive')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/accounts/7/restore')
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/accounts/7/set-primary')
    expect(call(fetchMock, 0)[1]?.method).toBe('POST')
  })

  it('adjusts the balance through POST /accounts/{id}/adjust', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        accountId: 7,
        currencyCode: 'USD',
        balanceMinor: 0,
        baseCurrencyCode: 'USD',
        balanceBaseMinor: 0,
        asOf: '2026-03-01',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await accountsApi.adjust(7, {
      newBalanceMinor: 0,
      occurredOn: '2026-03-01',
      reason: 'cash check',
    })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/accounts/7/adjust')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      newBalanceMinor: 0,
      occurredOn: '2026-03-01',
      reason: 'cash check',
    })
  })

  it('deletes with an optional force flag', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await accountsApi.remove(7)
    await accountsApi.remove(7, true)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/accounts/7?force=false')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/accounts/7?force=true')
    expect(call(fetchMock, 0)[1]?.method).toBe('DELETE')
  })

  it('fetches the daily balance series with from/to', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse([{ date: '2026-03-01', balanceMinor: 1, balanceBaseMinor: 1 }]),
      )
    vi.stubGlobal('fetch', fetchMock)

    const points = await accountsApi.balances(7, '2026-02-01', '2026-03-01')
    expect(points).toHaveLength(1)
    const [url] = call(fetchMock)
    expect(url).toBe('/api/v1/accounts/7/balances?from=2026-02-01&to=2026-03-01')
  })
})
