import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildTransactionQuery, transactionsApi, transfersApi } from './api'
import type { CreateTransactionInput } from './types'

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

const emptyPage = {
  content: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

const expenseInput: CreateTransactionInput = {
  accountId: 1,
  categoryId: 9,
  type: 'EXPENSE',
  direction: null,
  amountMinor: 1234,
  currencyCode: 'USD',
  exchangeRate: null,
  occurredOn: '2026-03-01',
  description: 'Coffee',
  merchant: null,
  payee: null,
  notes: null,
  status: 'POSTED',
  dedupeKey: null,
}

describe('buildTransactionQuery', () => {
  it('omits pagination unless the filter sets it', () => {
    expect(buildTransactionQuery({})).toEqual({})
    expect(buildTransactionQuery({ page: 2, size: 25 })).toEqual({ page: 2, size: 25 })
  })

  it('list always sends a page and a size', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage))
    vi.stubGlobal('fetch', fetchMock)

    await transactionsApi.list({})

    expect(call(fetchMock)[0]).toBe('/api/v1/transactions?page=0&size=50')
  })
})

describe('transactionsApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists with repeated multi-value params and a whitelist sort', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage))
    vi.stubGlobal('fetch', fetchMock)

    const page = await transactionsApi.list({
      q: 'rent',
      accountIds: [1, 3],
      types: ['EXPENSE'],
      from: '2026-01-01',
      sort: 'amount,desc',
      page: 1,
      size: 25,
    })

    expect(page.content).toEqual([])
    const [url] = call(fetchMock)
    expect(url).toBe(
      '/api/v1/transactions?q=rent&accountId=1&accountId=3&type=EXPENSE&from=2026-01-01&sort=amount%2Cdesc&page=1&size=25',
    )
  })

  it('reads the summary without pagination params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        count: 2,
        incomeBaseMinor: 0,
        expenseBaseMinor: 1234,
        netBaseMinor: -1234,
        baseCurrencyCode: 'USD',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const summary = await transactionsApi.summary({
      types: ['INCOME'],
      page: 3,
      size: 10,
      sort: 'amount,desc',
    })

    expect(summary.count).toBe(2)
    const [url] = call(fetchMock)
    expect(url).toBe('/api/v1/transactions/summary?type=INCOME')
  })

  it('fetches the filter options', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ accounts: [], categories: [], currencies: ['USD'] }))
    vi.stubGlobal('fetch', fetchMock)

    await transactionsApi.options()
    expect(call(fetchMock)[0]).toBe('/api/v1/transactions/filters/options')
  })

  it('creates with an Idempotency-Key header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 7 }))
    vi.stubGlobal('fetch', fetchMock)

    await transactionsApi.create(expenseInput, 'key-123')

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/transactions')
    expect(init?.method).toBe('POST')
    expect(init?.headers).toMatchObject({ 'Idempotency-Key': 'key-123' })
    expect(JSON.parse(String(init?.body))).toMatchObject({ amountMinor: 1234, type: 'EXPENSE' })
  })

  it('patches, deletes and probes a single transaction', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ id: 7, version: 1 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        jsonResponse({ duplicate: false, matchedTransactionId: null, confidence: 'NONE' }),
      )
      .mockResolvedValueOnce(jsonResponse({ id: 7 }))
    vi.stubGlobal('fetch', fetchMock)

    await transactionsApi.update(7, {
      accountId: 1,
      categoryId: 9,
      amountMinor: 5000,
      currencyCode: 'USD',
      occurredOn: '2026-03-02',
      description: 'Coffee',
      notes: '',
      version: 0,
    })
    await transactionsApi.remove(7)
    await transactionsApi.checkDuplicate({
      accountId: 1,
      occurredOn: '2026-03-01',
      amountMinor: 1234,
      currencyCode: 'USD',
      description: 'Coffee',
    })
    await transactionsApi.detail(7)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/transactions/7')
    expect(call(fetchMock, 0)[1]?.method).toBe('PATCH')
    expect(JSON.parse(String(call(fetchMock, 0)[1]?.body))).toMatchObject({ version: 0 })
    expect(call(fetchMock, 1)[1]?.method).toBe('DELETE')
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/transactions/check-duplicate')
    expect(call(fetchMock, 2)[1]?.method).toBe('POST')
    expect(call(fetchMock, 3)[0]).toBe('/api/v1/transactions/7')
    expect(call(fetchMock, 3)[1]?.method).toBe('GET')
  })

  it('reverses a row through the mirror endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 8, reversalOfTransactionId: 7 }))
    vi.stubGlobal('fetch', fetchMock)

    await transactionsApi.reverse(7, { reason: 'Duplicate charge', occurredOn: '2026-03-05' })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/transactions/7/reverse')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      reason: 'Duplicate charge',
      occurredOn: '2026-03-05',
    })
  })

  it('runs bulk actions against /transactions/bulk', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ updated: 2 }))
      .mockResolvedValueOnce(jsonResponse({ updated: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    const recategorised = await transactionsApi.recategorise([1, 2], 5)
    const deleted = await transactionsApi.bulkDelete([1])

    expect(recategorised.updated).toBe(2)
    expect(deleted.updated).toBe(1)
    expect(call(fetchMock, 0)[0]).toBe('/api/v1/transactions/bulk')
    expect(call(fetchMock, 0)[1]?.method).toBe('POST')
    expect(JSON.parse(String(call(fetchMock, 0)[1]?.body))).toEqual({ ids: [1, 2], categoryId: 5 })
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/transactions/bulk')
    expect(call(fetchMock, 1)[1]?.method).toBe('DELETE')
    expect(JSON.parse(String(call(fetchMock, 1)[1]?.body))).toEqual({ ids: [1] })
  })

  it('exports the filtered CSV with its filename', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('id,description\n7,Coffee\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/csv',
          'Content-Disposition': 'attachment; filename="spendio-transactions-2026-03-01.csv"',
        },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const { blob, filename } = await transactionsApi.exportCsv({ types: ['EXPENSE'] })

    expect(filename).toBe('spendio-transactions-2026-03-01.csv')
    expect(blob.size).toBeGreaterThan(0)
    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/transactions/export?type=EXPENSE')
    expect(init?.headers).toMatchObject({ Accept: 'text/csv' })
  })

  it('falls back to the default filename when none is sent', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('id\n', {
        status: 200,
        headers: { 'Content-Type': 'text/csv' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const { filename } = await transactionsApi.exportCsv({})
    expect(filename).toBe('spendio-transactions.csv')
  })
})

describe('transfersApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('creates a transfer with the source leg amount', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ transferGroupId: 'g-1' }))
    vi.stubGlobal('fetch', fetchMock)

    await transfersApi.create({
      fromAccountId: 1,
      toAccountId: 2,
      amountMinor: 20_000,
      currencyCode: 'USD',
      toAmountMinor: null,
      occurredOn: '2026-03-01',
      description: 'Rent share',
      notes: null,
    })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/transfers')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toMatchObject({
      fromAccountId: 1,
      toAccountId: 2,
      amountMinor: 20_000,
      currencyCode: 'USD',
      toAmountMinor: null,
    })
  })

  it('reverses a whole transfer group', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ transferGroupId: 'g-1' }))
    vi.stubGlobal('fetch', fetchMock)

    await transfersApi.reverse('g-1', { reason: 'Wrong accounts', occurredOn: '2026-03-02' })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/transfers/g-1/reverse')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      reason: 'Wrong accounts',
      occurredOn: '2026-03-02',
    })
  })
})
