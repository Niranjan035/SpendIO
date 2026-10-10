import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { transactionsApi, transfersApi } from './api'
import {
  transactionsKeys,
  transfersKeys,
  useCreateTransaction,
  useCreateTransfer,
  useDeleteTransaction,
  useReverseTransaction,
  useReverseTransfer,
  useTransaction,
  useTransactionFilterOptions,
  useTransactions,
  useTransactionSummary,
  useUpdateTransaction,
} from './hooks'
import type { Transaction, TransactionFilter, TransactionSummary } from './types'

vi.mock('./api', () => ({
  transactionsApi: {
    list: vi.fn(),
    summary: vi.fn(),
    options: vi.fn(),
    detail: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    reverse: vi.fn(),
    recategorise: vi.fn(),
    bulkDelete: vi.fn(),
    checkDuplicate: vi.fn(),
    exportCsv: vi.fn(),
  },
  transfersApi: {
    create: vi.fn(),
    reverse: vi.fn(),
  },
}))

const transaction: Transaction = {
  id: 7,
  account: { id: 1, name: 'Checking', currencyCode: 'USD' },
  category: { id: 9, name: 'Groceries', type: 'EXPENSE', color: null, icon: null },
  type: 'EXPENSE',
  direction: 'OUTFLOW',
  amountMinor: 1234,
  currencyCode: 'USD',
  exchangeRate: 1,
  baseAmountMinor: 1234,
  baseCurrencyCode: 'USD',
  occurredOn: '2026-03-01',
  description: 'Coffee beans',
  merchant: null,
  payee: null,
  notes: null,
  status: 'POSTED',
  transferGroupId: null,
  reversalOfTransactionId: null,
  reversedAt: null,
  recurringTransactionId: null,
  csvImportId: null,
  createdAt: '2026-03-01T10:00:00Z',
  updatedAt: '2026-03-01T10:00:00Z',
  version: 0,
}

const summary: TransactionSummary = {
  count: 1,
  incomeBaseMinor: 0,
  expenseBaseMinor: 1234,
  netBaseMinor: -1234,
  baseCurrencyCode: 'USD',
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
  it('loads the ledger page into the cache', async () => {
    const page = {
      content: [transaction],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }
    vi.mocked(transactionsApi.list).mockResolvedValue(page)
    const filter: TransactionFilter = { types: ['EXPENSE'], page: 0, size: 50 }
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useTransactions(filter), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content).toEqual([transaction])
    expect(transactionsApi.list).toHaveBeenCalledWith(filter)
    expect(queryClient.getQueryData(transactionsKeys.list(filter))).toBe(page)
  })

  it('loads the summary and the filter options', async () => {
    vi.mocked(transactionsApi.summary).mockResolvedValue(summary)
    vi.mocked(transactionsApi.options).mockResolvedValue({
      accounts: [],
      categories: [],
      currencies: ['USD'],
      minDate: null,
      maxDate: null,
      typeCounts: {},
    })
    const { queryClient, wrapper } = createWrapper()
    const filter: TransactionFilter = { types: ['EXPENSE'] }

    const { result: summaryHook } = renderHook(() => useTransactionSummary(filter), { wrapper })
    const { result: optionsHook } = renderHook(() => useTransactionFilterOptions(), { wrapper })

    await waitFor(() => expect(summaryHook.current.isSuccess).toBe(true))
    await waitFor(() => expect(optionsHook.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(transactionsKeys.summary(filter))).toEqual(summary)
    expect(queryClient.getQueryData(transactionsKeys.options)).toBeDefined()
  })

  it('loads a single transaction', async () => {
    vi.mocked(transactionsApi.detail).mockResolvedValue(transaction)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useTransaction(7), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(transactionsKeys.detail(7))).toBe(transaction)
  })
})

describe('mutations', () => {
  it('creates a transaction and invalidates the ledger caches', async () => {
    vi.mocked(transactionsApi.create).mockResolvedValue(transaction)
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['transactions', 'list', { page: 0 }], { content: [] })

    const { result } = renderHook(() => useCreateTransaction(), { wrapper })
    act(() =>
      result.current.mutate({
        input: {
          accountId: 1,
          categoryId: 9,
          type: 'EXPENSE',
          direction: null,
          amountMinor: 1234,
          currencyCode: 'USD',
          exchangeRate: null,
          occurredOn: '2026-03-01',
          description: 'Coffee beans',
          merchant: null,
          payee: null,
          notes: null,
          status: 'POSTED',
          dedupeKey: null,
        },
        idempotencyKey: 'key-1',
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(transactionsApi.create).toHaveBeenCalledOnce()
    expect(queryClient.getQueryState(['transactions', 'list', { page: 0 }])?.isInvalidated).toBe(
      true,
    )
  })

  it('updates a transaction and refreshes its detail cache', async () => {
    const updated = { ...transaction, description: 'Coffee beans (retry)' }
    vi.mocked(transactionsApi.update).mockResolvedValue(updated)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateTransaction(), { wrapper })
    act(() =>
      result.current.mutate({
        id: 7,
        input: {
          accountId: 1,
          categoryId: 9,
          amountMinor: 1234,
          currencyCode: 'USD',
          occurredOn: '2026-03-01',
          description: 'Coffee beans (retry)',
          notes: '',
          version: 0,
        },
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(transactionsKeys.detail(7))).toEqual(updated)
  })

  it('deletes a transaction and drops its detail cache', async () => {
    vi.mocked(transactionsApi.remove).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(transactionsKeys.detail(7), transaction)

    const { result } = renderHook(() => useDeleteTransaction(), { wrapper })
    act(() => result.current.mutate(7))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(transactionsApi.remove).toHaveBeenCalledWith(7)
    expect(queryClient.getQueryState(transactionsKeys.detail(7))).toBeUndefined()
  })

  it('creates a transfer and refreshes the transfer and ledger caches', async () => {
    vi.mocked(transfersApi.create).mockResolvedValue({
      transferGroupId: 'g-1',
      fromTransaction: transaction,
      toTransaction: { ...transaction, id: 8, direction: 'INFLOW' },
    })
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['transactions', 'list', { page: 0 }], { content: [] })
    queryClient.setQueryData(transfersKeys.all, { content: [] })

    const { result } = renderHook(() => useCreateTransfer(), { wrapper })
    act(() =>
      result.current.mutate({
        fromAccountId: 1,
        toAccountId: 2,
        amountMinor: 20_000,
        currencyCode: 'USD',
        toAmountMinor: null,
        occurredOn: '2026-03-01',
        description: null,
        notes: null,
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(transfersApi.create).toHaveBeenCalledOnce()
    expect(queryClient.getQueryState(['transactions', 'list', { page: 0 }])?.isInvalidated).toBe(
      true,
    )
    expect(queryClient.getQueryState(transfersKeys.all)?.isInvalidated).toBe(true)
  })

  it('reverses a transaction and refreshes the ledger cache', async () => {
    vi.mocked(transactionsApi.reverse).mockResolvedValue({
      ...transaction,
      id: 8,
      reversalOfTransactionId: 7,
    })
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['transactions', 'list', { page: 0 }], { content: [] })

    const { result } = renderHook(() => useReverseTransaction(), { wrapper })
    act(() => result.current.mutate({ id: 7, input: { reason: 'Oops', occurredOn: '2026-03-02' } }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(transactionsApi.reverse).toHaveBeenCalledWith(7, {
      reason: 'Oops',
      occurredOn: '2026-03-02',
    })
    expect(queryClient.getQueryState(['transactions', 'list', { page: 0 }])?.isInvalidated).toBe(
      true,
    )
  })

  it('reverses a transfer group and refreshes both caches', async () => {
    vi.mocked(transfersApi.reverse).mockResolvedValue({
      transferGroupId: 'g-1',
      fromTransaction: transaction,
      toTransaction: { ...transaction, id: 8, direction: 'INFLOW' },
    })
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['transactions', 'list', { page: 0 }], { content: [] })
    queryClient.setQueryData(transfersKeys.all, { content: [] })

    const { result } = renderHook(() => useReverseTransfer(), { wrapper })
    act(() =>
      result.current.mutate({
        transferGroupId: 'g-1',
        input: { reason: 'Wrong accounts', occurredOn: '2026-03-02' },
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(transfersApi.reverse).toHaveBeenCalledWith('g-1', {
      reason: 'Wrong accounts',
      occurredOn: '2026-03-02',
    })
    expect(queryClient.getQueryState(['transactions', 'list', { page: 0 }])?.isInvalidated).toBe(
      true,
    )
    expect(queryClient.getQueryState(transfersKeys.all)?.isInvalidated).toBe(true)
  })
})
