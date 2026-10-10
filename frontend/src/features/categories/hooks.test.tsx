import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { categoriesApi } from './api'
import {
  categoryKeys,
  useArchiveCategory,
  useCategories,
  useCreateCategory,
  useDeleteCategory,
  useRestoreCategory,
  useUpdateCategory,
} from './hooks'
import type { Category, CategoryFilter } from './types'

vi.mock('./api', () => ({
  categoriesApi: {
    list: vi.fn(),
    create: vi.fn(),
    detail: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    archive: vi.fn(),
    restore: vi.fn(),
  },
}))

const category: Category = {
  id: 9,
  userId: 1,
  parentId: null,
  name: 'Food & Groceries',
  type: 'EXPENSE',
  icon: 'utensils',
  color: '#16a34a',
  isDefault: true,
  isSystem: false,
  sortOrder: 0,
  excludeFromBudgets: false,
  status: 'ACTIVE',
  archivedAt: null,
  createdAt: '2026-01-01T09:00:00Z',
  updatedAt: '2026-01-01T09:00:00Z',
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
  it('loads the category page into the cache', async () => {
    const page = {
      content: [category],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }
    vi.mocked(categoriesApi.list).mockResolvedValue(page)
    const filter: CategoryFilter = { status: 'ACTIVE', page: 0, size: 25 }

    const { queryClient, wrapper } = createWrapper()
    const { result } = renderHook(() => useCategories(filter), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.content).toEqual([category])
    expect(categoriesApi.list).toHaveBeenCalledWith(filter)
    expect(queryClient.getQueryData(categoryKeys.list(filter))).toBe(page)
  })
})

describe('mutations', () => {
  it('creates a category and invalidates the lists', async () => {
    vi.mocked(categoriesApi.create).mockResolvedValue(category)
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['categories', 'list', { status: 'ACTIVE', page: 0, size: 25 }], {
      content: [],
    })

    const { result } = renderHook(() => useCreateCategory(), { wrapper })
    act(() =>
      result.current.mutate({
        name: 'Food & Groceries',
        type: 'EXPENSE',
        icon: 'utensils',
        color: '#16a34a',
        excludeFromBudgets: false,
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(categoriesApi.create).toHaveBeenCalledOnce()
    expect(
      queryClient.getQueryState(['categories', 'list', { status: 'ACTIVE', page: 0, size: 25 }])
        ?.isInvalidated,
    ).toBe(true)
  })

  it('updates a category and refreshes its detail cache', async () => {
    const updated = { ...category, name: 'Groceries' }
    vi.mocked(categoriesApi.update).mockResolvedValue(updated)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateCategory(), { wrapper })
    act(() => result.current.mutate({ id: 9, input: { name: 'Groceries' } }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(categoryKeys.detail(9))).toEqual(updated)
  })

  it('archives and restores categories', async () => {
    vi.mocked(categoriesApi.archive).mockResolvedValue({ ...category, status: 'ARCHIVED' })
    vi.mocked(categoriesApi.restore).mockResolvedValue(category)
    const { wrapper } = createWrapper()

    const { result: archived } = renderHook(() => useArchiveCategory(), { wrapper })
    const { result: restored } = renderHook(() => useRestoreCategory(), { wrapper })

    act(() => archived.current.mutate(9))
    await waitFor(() => expect(archived.current.isSuccess).toBe(true))
    act(() => restored.current.mutate(9))
    await waitFor(() => expect(restored.current.isSuccess).toBe(true))

    expect(categoriesApi.archive).toHaveBeenCalledWith(9)
    expect(categoriesApi.restore).toHaveBeenCalledWith(9)
  })

  it('deletes a category and evicts its detail cache', async () => {
    vi.mocked(categoriesApi.remove).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(categoryKeys.detail(9), category)

    const { result } = renderHook(() => useDeleteCategory(), { wrapper })
    act(() => result.current.mutate(9))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(categoriesApi.remove).toHaveBeenCalledWith(9)
    expect(queryClient.getQueryState(categoryKeys.detail(9))?.data).toBeUndefined()
  })

  it('invalidates the transaction filter options so the form dropdown refreshes', async () => {
    vi.mocked(categoriesApi.create).mockResolvedValue(category)
    const { queryClient, wrapper } = createWrapper()
    queryClient.setQueryData(['transactions', 'options'], { accounts: [], categories: [] })

    const { result } = renderHook(() => useCreateCategory(), { wrapper })
    act(() =>
      result.current.mutate({
        name: 'Food & Groceries',
        type: 'EXPENSE',
        icon: 'utensils',
        color: '#16a34a',
        excludeFromBudgets: false,
      }),
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryState(['transactions', 'options'])?.isInvalidated).toBe(true)
  })
})