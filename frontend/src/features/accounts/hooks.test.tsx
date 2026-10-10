import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { accountsApi } from './api'
import {
  accountsKeys,
  useAccount,
  useAccountBalance,
  useAccounts,
  useAccountSummary,
  useAdjustBalance,
  useArchiveAccount,
  useCreateAccount,
  useDailyBalances,
  useDeleteAccount,
  useRestoreAccount,
  useSetPrimaryAccount,
  useUpdateAccount,
} from './hooks'
import type { Account, AccountFilter, AccountTotals } from './types'

vi.mock('./api', () => ({
  accountsApi: {
    list: vi.fn(),
    create: vi.fn(),
    summary: vi.fn(),
    detail: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    archive: vi.fn(),
    restore: vi.fn(),
    setPrimary: vi.fn(),
    balance: vi.fn(),
    balances: vi.fn(),
    adjust: vi.fn(),
  },
}))

const account: Account = {
  id: 7,
  name: 'Everyday checking',
  type: 'BANK',
  currencyCode: 'EUR',
  openingBalanceMinor: 1000,
  openingBalanceDate: '2026-01-01',
  institution: 'Revolut',
  last4: null,
  creditLimitMinor: null,
  billingDay: null,
  dueDay: null,
  icon: null,
  color: null,
  notes: null,
  primary: true,
  status: 'ACTIVE',
  transactionCount: 0,
  createdAt: '2026-01-01T09:00:00Z',
  balanceMinor: 125000,
  balanceBaseMinor: 125000,
  baseCurrencyCode: 'EUR',
  availableCreditMinor: null,
}

const totals: AccountTotals = {
  totalBaseMinor: 125000,
  baseCurrencyCode: 'EUR',
  byType: { BANK: 125000 },
  byCurrency: { EUR: 125000 },
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
  it('loads the account page into the cache', async () => {
    const page = {
      content: [account],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }
    vi.mocked(accountsApi.list).mockResolvedValue(page)
    const filter: AccountFilter = { status: 'ACTIVE', page: 0, size: 25 }
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useAccounts(filter), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content).toEqual([account])
    expect(accountsApi.list).toHaveBeenCalledWith(filter)
    expect(queryClient.getQueryData(accountsKeys.list(filter))).toBe(page)
  })

  it('loads the summary into the cache', async () => {
    vi.mocked(accountsApi.summary).mockResolvedValue(totals)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useAccountSummary(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(accountsKeys.summary)).toEqual(totals)
  })

  it('loads a single account, its balance and its daily series', async () => {
    vi.mocked(accountsApi.detail).mockResolvedValue(account)
    vi.mocked(accountsApi.balance).mockResolvedValue({
      accountId: 7,
      currencyCode: 'EUR',
      balanceMinor: 125000,
      baseCurrencyCode: 'EUR',
      balanceBaseMinor: 125000,
      asOf: '2026-03-01',
      availableCreditMinor: null,
    })
    vi.mocked(accountsApi.balances).mockResolvedValue([
      { date: '2026-03-01', balanceMinor: 125000, balanceBaseMinor: 125000 },
    ])
    const { queryClient, wrapper } = createWrapper()

    const { result: detail } = renderHook(() => useAccount(7), { wrapper })
    const { result: balance } = renderHook(() => useAccountBalance(7), { wrapper })
    const { result: series } = renderHook(() => useDailyBalances(7, '2026-02-01', '2026-03-01'), {
      wrapper,
    })

    await waitFor(() => expect(detail.current.data?.id).toBe(7))
    await waitFor(() => expect(balance.current.isSuccess).toBe(true))
    await waitFor(() => expect(series.current.isSuccess).toBe(true))

    expect(queryClient.getQueryData(accountsKeys.detail(7))).toBe(account)
    expect(accountsApi.balances).toHaveBeenCalledWith(7, '2026-02-01', '2026-03-01')
  })
})

describe('mutations', () => {
  it('creates an account and invalidates the lists', async () => {
    vi.mocked(accountsApi.create).mockResolvedValue(account)
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['accounts', 'list', { status: 'ACTIVE' }], { content: [] })

    const { result } = renderHook(() => useCreateAccount(), { wrapper })
    act(() =>
      result.current.mutate({
        name: 'Everyday checking',
        type: 'BANK',
        currencyCode: 'EUR',
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
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(accountsApi.create).toHaveBeenCalledOnce()
    expect(
      queryClient.getQueryState(['accounts', 'list', { status: 'ACTIVE' }])?.isInvalidated,
    ).toBe(true)
  })

  it('updates an account and refreshes its detail cache', async () => {
    const updated = { ...account, name: 'Everyday' }
    vi.mocked(accountsApi.update).mockResolvedValue(updated)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateAccount(), { wrapper })
    act(() => result.current.mutate({ id: 7, input: { name: 'Everyday' } }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(accountsKeys.detail(7))).toEqual(updated)
  })

  it('archives, restores and promotes accounts', async () => {
    vi.mocked(accountsApi.archive).mockResolvedValue({ ...account, status: 'ARCHIVED' })
    vi.mocked(accountsApi.restore).mockResolvedValue(account)
    vi.mocked(accountsApi.setPrimary).mockResolvedValue({ ...account, primary: true })
    const { wrapper } = createWrapper()

    const { result: archived } = renderHook(() => useArchiveAccount(), { wrapper })
    const { result: restored } = renderHook(() => useRestoreAccount(), { wrapper })
    const { result: primary } = renderHook(() => useSetPrimaryAccount(), { wrapper })

    act(() => archived.current.mutate(7))
    await waitFor(() => expect(archived.current.isSuccess).toBe(true))
    act(() => restored.current.mutate(7))
    await waitFor(() => expect(restored.current.isSuccess).toBe(true))
    act(() => primary.current.mutate(7))
    await waitFor(() => expect(primary.current.isSuccess).toBe(true))

    expect(accountsApi.archive).toHaveBeenCalledWith(7)
    expect(accountsApi.restore).toHaveBeenCalledWith(7)
    expect(accountsApi.setPrimary).toHaveBeenCalledWith(7)
  })

  it('deletes an account (optionally forced)', async () => {
    vi.mocked(accountsApi.remove).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useDeleteAccount(), { wrapper })
    act(() => result.current.mutate({ id: 7, force: true }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(accountsApi.remove).toHaveBeenCalledWith(7, true)
  })

  it('adjusts the balance and refetches the current balance', async () => {
    vi.mocked(accountsApi.adjust).mockResolvedValue({
      accountId: 7,
      currencyCode: 'EUR',
      balanceMinor: 0,
      baseCurrencyCode: 'EUR',
      balanceBaseMinor: 0,
      asOf: '2026-03-02',
      availableCreditMinor: null,
    })
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(accountsKeys.balance(7), {
      accountId: 7,
      currencyCode: 'EUR',
      balanceMinor: 125000,
      baseCurrencyCode: 'EUR',
      balanceBaseMinor: 125000,
      asOf: '2026-03-01',
      availableCreditMinor: null,
    })

    const { result } = renderHook(() => useAdjustBalance(), { wrapper })
    act(() =>
      result.current.mutate({
        id: 7,
        input: { newBalanceMinor: 0, occurredOn: '2026-03-02', reason: 'cash check' },
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(accountsApi.adjust).toHaveBeenCalledWith(7, {
      newBalanceMinor: 0,
      occurredOn: '2026-03-02',
      reason: 'cash check',
    })
    expect(queryClient.getQueryState(accountsKeys.balance(7))?.isInvalidated).toBe(true)
  })
})
