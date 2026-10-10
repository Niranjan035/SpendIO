import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { analyticsApi, type AnalyticsRange } from './api'

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

const range: AnalyticsRange = {
  from: '2026-10-01',
  to: '2026-10-31',
  granularity: 'monthly',
}

describe('analyticsApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('queries cash-flow with the range and granularity', async () => {
    const fetchMock = vi.fn().mockImplementation(() => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await analyticsApi.cashFlow(range)

    expect(call(fetchMock)[0]).toBe(
      '/api/v1/analytics/cash-flow?from=2026-10-01&to=2026-10-31&granularity=monthly',
    )
  })

  it('spending-trend and income-vs-expense share the range contract; net-worth is date-only', async () => {
    const fetchMock = vi.fn().mockImplementation(() => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

await analyticsApi.spendingTrend(range)
    await analyticsApi.incomeVsExpense(range)

    expect(call(fetchMock, 0)[0]).toBe(
      '/api/v1/analytics/spending-trend?from=2026-10-01&to=2026-10-31&granularity=monthly',
    )
    expect(call(fetchMock, 1)[0]).toBe(
      '/api/v1/analytics/income-vs-expense?from=2026-10-01&to=2026-10-31&granularity=monthly',
    )

    await analyticsApi.netWorth(range)
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/analytics/net-worth?from=2026-10-01&to=2026-10-31')
  })

  it('category-breakdown sends type, limit and parentOnly and omits undefined', async () => {
    const fetchMock = vi.fn().mockImplementation(() => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await analyticsApi.categoryBreakdown(range, 'EXPENSE', 20, false)

    expect(call(fetchMock)[0]).toBe(
      '/api/v1/analytics/category-breakdown'
        + '?from=2026-10-01&to=2026-10-31&granularity=monthly&type=EXPENSE&limit=20&parentOnly=false',
    )
  })

  it('monthly-comparison sends the year and snapshot endpoints have no query', async () => {
    const fetchMock = vi.fn().mockImplementation(() => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await analyticsApi.monthlyComparison(2026)
    await analyticsApi.budgetUtilization()
    await analyticsApi.goalProgress()
    await analyticsApi.accounts()

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/analytics/monthly-comparison?year=2026')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/analytics/budget-utilization')
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/analytics/goal-progress')
    expect(call(fetchMock, 3)[0]).toBe('/api/v1/analytics/accounts')
  })

  it('dashboard endpoints carry the summary month, limit and days', async () => {
    const fetchMock = vi.fn().mockImplementation(() => jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    await analyticsApi.dashboardSummary('2026-10')
    await analyticsApi.recentTransactions(5)
    await analyticsApi.upcomingRecurring(30)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/dashboard/summary?month=2026-10')
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/dashboard/recent-transactions?limit=5')
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/dashboard/upcoming-recurring?days=30')
  })
})
