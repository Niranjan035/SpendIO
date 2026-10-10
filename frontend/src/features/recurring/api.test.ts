import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { buildRecurringQuery, recurringApi } from './api'
import type { CreateRecurringInput } from './types'

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

const createInput: CreateRecurringInput = {
  accountId: 1,
  toAccountId: null,
  categoryId: 9,
  type: 'EXPENSE',
  amountMinor: 1234,
  currencyCode: 'USD',
  description: 'Rent',
  notes: null,
  frequency: 'MONTHLY',
  intervalCount: 1,
  dayOfMonth: 1,
  dayOfWeek: null,
  skipWeekends: false,
  startDate: '2026-03-01',
  endDate: null,
  autoPost: true,
  maxRetries: 3,
  targetOccurrenceDate: null,
}

describe('buildRecurringQuery', () => {
  it('omits empty filters and keeps pagination', () => {
    expect(buildRecurringQuery({})).toEqual({})
    expect(buildRecurringQuery({ status: 'PAUSED', dueWithinDays: 30, page: 2, size: 25 })).toEqual({
      status: 'PAUSED',
      dueWithinDays: 30,
      page: 2,
      size: 25,
    })
  })

  it('list always sends a page and a size', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage))
    vi.stubGlobal('fetch', fetchMock)

    await recurringApi.list({})

    expect(call(fetchMock)[0]).toBe('/api/v1/recurring-transactions?page=0&size=50')
  })
})

describe('recurringApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists with the E.9 filter params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage))
    vi.stubGlobal('fetch', fetchMock)

    await recurringApi.list({ status: 'ACTIVE', type: 'EXPENSE', dueWithinDays: 7 })

    const [url] = call(fetchMock)
    expect(url).toBe(
      '/api/v1/recurring-transactions?status=ACTIVE&type=EXPENSE&dueWithinDays=7&page=0&size=50',
    )
  })

  it('creates a rule and reads the upcoming window', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ rule: { id: 7 }, occurrences: [] }, 201))
      .mockResolvedValueOnce(jsonResponse([], 200))
    vi.stubGlobal('fetch', fetchMock)

    await recurringApi.create(createInput)
    await recurringApi.upcoming(14)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/recurring-transactions')
    expect(call(fetchMock, 0)[1]?.method).toBe('POST')
    expect(JSON.parse(String(call(fetchMock, 0)[1]?.body))).toMatchObject({
      accountId: 1,
      categoryId: 9,
      type: 'EXPENSE',
      amountMinor: 1234,
    })
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/recurring-transactions/upcoming?days=14')
  })

  it('patches, pauses, resumes and cancels a rule', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() => jsonResponse({ id: 7, status: 'PAUSED' }))
    vi.stubGlobal('fetch', fetchMock)

    await recurringApi.update(7, { description: 'Rent (annual)' })
    await recurringApi.pause(7)
    await recurringApi.resume(7)
    await recurringApi.cancel(7)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/recurring-transactions/7')
    expect(call(fetchMock, 0)[1]?.method).toBe('PATCH')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/recurring-transactions/7/pause')
    expect(call(fetchMock, 1)[1]?.method).toBe('POST')
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/recurring-transactions/7/resume')
    expect(call(fetchMock, 3)[0]).toBe('/api/v1/recurring-transactions/7/cancel')
  })

  it('deletes without a body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await recurringApi.remove(7)

    expect(call(fetchMock)[0]).toBe('/api/v1/recurring-transactions/7')
    expect(call(fetchMock)[1]?.method).toBe('DELETE')
  })

  it('runs now with an Idempotency-Key header', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse({ id: 11, status: 'SUCCESS', transactionId: 55, attempts: 1 }, 201),
      )
    vi.stubGlobal('fetch', fetchMock)

    const run = await recurringApi.runNow(7, 'key-123')

    expect(run.transactionId).toBe(55)
    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/recurring-transactions/7/run-now')
    expect(init?.method).toBe('POST')
    expect(init?.headers).toMatchObject({ 'Idempotency-Key': 'key-123' })
  })

  it('fetches the occurrence preview and the run history', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ occurrences: [{ date: '2026-04-01' }] }))
      .mockResolvedValueOnce(jsonResponse(emptyPage))
    vi.stubGlobal('fetch', fetchMock)

    await recurringApi.preview(7, 5)
    const runs = await recurringApi.runs(7, 1, 10)

    expect(runs.content).toEqual([])
    expect(call(fetchMock, 0)[0]).toBe('/api/v1/recurring-transactions/7/preview?count=5')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/recurring-transactions/7/runs?page=1&size=10')
  })
})