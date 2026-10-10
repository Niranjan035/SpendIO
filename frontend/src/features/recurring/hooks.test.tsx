import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { recurringApi } from './api'
import {
  recurringKeys,
  useCreateRecurring,
  usePauseRecurring,
  useRecurring,
  useRecurringRuns,
  useRecurrings,
  useRunNowRecurring,
  useUpcomingRecurrings,
} from './hooks'
import type { RecurringTransaction } from './types'

vi.mock('./api', () => ({
  recurringApi: {
    list: vi.fn(),
    create: vi.fn(),
    upcoming: vi.fn(),
    detail: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    cancel: vi.fn(),
    runNow: vi.fn(),
    preview: vi.fn(),
    runs: vi.fn(),
  },
}))

const rule: RecurringTransaction = {
  id: 7,
  account: { id: 1, name: 'Checking', currencyCode: 'USD' },
  toAccount: null,
  category: { id: 9, name: 'Rent', type: 'EXPENSE', color: null, icon: null },
  type: 'EXPENSE',
  direction: 'OUTFLOW',
  amountMinor: 123400,
  currencyCode: 'USD',
  baseAmountMinor: 123400,
  description: 'Rent',
  notes: null,
  frequency: 'MONTHLY',
  intervalCount: 1,
  dayOfMonth: 1,
  dayOfWeek: null,
  skipWeekends: false,
  startDate: '2026-03-01',
  endDate: null,
  targetOccurrenceDate: null,
  nextRunDate: '2026-04-01',
  lastRunDate: '2026-03-01',
  status: 'ACTIVE',
  autoPost: true,
  maxRetries: 3,
  retryCount: 0,
  lastError: null,
  failureReason: null,
  generatedCount: 2,
  createdAt: '2026-03-01T09:00:00Z',
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('queries', () => {
  it('loads the filtered rule list into the cache', async () => {
    const filteredPage = {
      content: [rule],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }
    vi.mocked(recurringApi.list).mockResolvedValue(filteredPage)
    const { queryClient, wrapper } = createWrapper()
    const filter = { status: 'ACTIVE' as const, page: 0, size: 50 }

    const { result } = renderHook(() => useRecurrings(filter), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content).toEqual([rule])
    expect(recurringApi.list).toHaveBeenCalledWith(filter)
    expect(queryClient.getQueryData(recurringKeys.list(filter))).toBe(filteredPage)
  })

  it('loads the upcoming window and a single rule', async () => {
    vi.mocked(recurringApi.upcoming).mockResolvedValue([rule])
    vi.mocked(recurringApi.detail).mockResolvedValue(rule)
    const { queryClient, wrapper } = createWrapper()

    const { result: upcoming } = renderHook(() => useUpcomingRecurrings(30), { wrapper })
    const { result: detail } = renderHook(() => useRecurring(7), { wrapper })

    await waitFor(() => expect(upcoming.current.isSuccess).toBe(true))
    await waitFor(() => expect(detail.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(recurringKeys.upcoming(30))).toEqual([rule])
    expect(queryClient.getQueryData(recurringKeys.detail(7))).toBe(rule)
  })

  it('loads the run history page', async () => {
    const runsPage = {
      content: [
        { id: 11, scheduledFor: '2026-03-01', executedAt: '2026-03-01T10:00:00Z', status: 'SUCCESS' as const, transactionId: 55, attempts: 1, errorMessage: null },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }
    vi.mocked(recurringApi.runs).mockResolvedValue(runsPage)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useRecurringRuns(7, 0), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(recurringApi.runs).toHaveBeenCalledWith(7, 0)
    expect(result.current.data?.content[0]?.transactionId).toBe(55)
  })
})

describe('mutations', () => {
  it('creates a rule and invalidates the list cache', async () => {
    vi.mocked(recurringApi.create).mockResolvedValue({ rule, occurrences: [] })
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['recurring', 'list', {}], { content: [] })

    const { result } = renderHook(() => useCreateRecurring(), { wrapper })
    act(() =>
      result.current.mutate({
        accountId: 1,
        toAccountId: null,
        categoryId: 9,
        type: 'EXPENSE',
        amountMinor: 123400,
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
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryState(['recurring', 'list', {}])?.isInvalidated).toBe(true)
    expect(queryClient.getQueryData(recurringKeys.detail(7))).toBe(rule)
  })

  it('pauses a rule and refreshes the upcoming window', async () => {
    const paused = { ...rule, status: 'PAUSED' as const }
    vi.mocked(recurringApi.pause).mockResolvedValue(paused)
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(recurringKeys.upcoming(0), [rule])

    const { result } = renderHook(() => usePauseRecurring(), { wrapper })
    act(() => result.current.mutate(7))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(recurringKeys.detail(7))).toBe(paused)
    expect(queryClient.getQueryState(recurringKeys.upcoming(0))?.isInvalidated).toBe(true)
  })

  it('runs now and refreshes detail, runs and upcoming', async () => {
    vi.mocked(recurringApi.runNow).mockResolvedValue({
      id: 11,
      scheduledFor: '2026-04-01',
      executedAt: '2026-04-01T10:00:00Z',
      status: 'SUCCESS',
      transactionId: 55,
      attempts: 1,
      errorMessage: null,
    })
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(recurringKeys.detail(7), rule)
    queryClient.setQueryData(recurringKeys.runs(7, 0), {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    })

    const { result } = renderHook(() => useRunNowRecurring(), { wrapper })
    act(() => result.current.mutate({ id: 7, idempotencyKey: 'key-1' }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(recurringApi.runNow).toHaveBeenCalledWith(7, 'key-1')
    expect(queryClient.getQueryState(recurringKeys.detail(7))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(recurringKeys.runs(7, 0))?.isInvalidated).toBe(true)
  })
})