import { afterEach, describe, expect, it, vi } from 'vitest'
import { fxApi } from './api'

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

describe('fxApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('converts via POST /fx/convert with integer minor units', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        fromAmountMinor: 1234,
        toAmountMinor: 1333,
        rate: 1.08,
        rateDate: '2026-10-07',
        source: 'provider',
        stale: false,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await fxApi.convert({
      fromCurrency: 'EUR',
      toCurrency: 'USD',
      amountMinor: 1234,
      date: '2026-10-07',
    })

    expect(result.toAmountMinor).toBe(1333)
    expect(result.stale).toBe(false)
    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/fx/convert')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      fromCurrency: 'EUR',
      toCurrency: 'USD',
      amountMinor: 1234,
      date: '2026-10-07',
    })
  })

  it('reads the resolved rate from GET /fx/rates/current', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        baseCurrencyCode: 'EUR',
        quoteCurrencyCode: 'USD',
        rate: 1.08,
        rateDate: '2026-10-07',
        source: 'inverse(USD/EUR)',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const rate = await fxApi.currentRate('EUR', 'USD', '2026-10-07')
    expect(rate.source).toBe('inverse(USD/EUR)')
    const [url] = call(fetchMock)
    expect(url).toBe('/api/v1/fx/rates/current?from=EUR&to=USD&date=2026-10-07')
  })

  it('omits an unspecified date when reading the current rate', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        baseCurrencyCode: 'USD',
        quoteCurrencyCode: 'JPY',
        rate: 150,
        rateDate: '2026-10-07',
        source: 'provider',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await fxApi.currentRate('USD', 'JPY')
    const [url] = call(fetchMock)
    expect(url).toBe('/api/v1/fx/rates/current?from=USD&to=JPY')
  })

  it('lists rate history from GET /fx/rates', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse([
        {
          baseCurrencyCode: 'EUR',
          quoteCurrencyCode: 'USD',
          rate: 1.08,
          rateDate: '2026-10-07',
          source: 'manual',
        },
      ]),
    )
    vi.stubGlobal('fetch', fetchMock)

    const history = await fxApi.history({ base: 'EUR', quote: 'USD' })
    expect(history).toHaveLength(1)
    const [url] = call(fetchMock)
    expect(url).toBe('/api/v1/fx/rates?base=EUR&quote=USD')
  })

  it('stores a manual override via POST /fx/rates', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        baseCurrencyCode: 'EUR',
        quoteCurrencyCode: 'USD',
        rate: 1.11,
        rateDate: '2026-10-07',
        source: 'manual',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const saved = await fxApi.overrideRate({
      baseCurrencyCode: 'EUR',
      quoteCurrencyCode: 'USD',
      rate: 1.11,
      rateDate: '2026-10-07',
    })
    expect(saved.source).toBe('manual')
    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/fx/rates')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toMatchObject({ rate: 1.11, rateDate: '2026-10-07' })
  })

  it('runs the daily refresh via POST /fx/refresh', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        job: 'fx-rate-refresh',
        rateDate: '2026-10-07',
        provider: 'stub',
        rows: 42,
        status: 'SUCCESS',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const summary = await fxApi.refresh()
    expect(summary.rows).toBe(42)
    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/fx/refresh')
    expect(init?.method).toBe('POST')
  })
})
