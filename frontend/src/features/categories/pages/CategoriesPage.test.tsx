import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { ApiError } from '@/services/http/ApiError'
import { categoriesApi } from '../api'
import type { Category } from '../types'
import { CategoriesPage } from './CategoriesPage'

vi.mock('../api', () => ({
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

const grocery: Category = {
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

const salary: Category = {
  id: 12,
  userId: 1,
  parentId: null,
  name: 'Salary',
  type: 'INCOME',
  icon: 'briefcase',
  color: '#22c55e',
  isDefault: true,
  isSystem: false,
  sortOrder: 0,
  excludeFromBudgets: true,
  status: 'ACTIVE',
  archivedAt: null,
  createdAt: '2026-01-01T09:00:00Z',
  updatedAt: '2026-01-01T09:00:00Z',
}

const oldCategory: Category = {
  ...grocery,
  id: 3,
  sortOrder: 3,
  status: 'ARCHIVED',
  archivedAt: '2026-04-01T09:00:00Z',
}

function page(content: Category[], totalPages = 1) {
  return {
    content,
    page: 0,
    size: 50,
    totalElements: content.length,
    totalPages,
    first: true,
    last: totalPages <= 1,
  }
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <CategoriesPage />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(categoriesApi.list).mockResolvedValue(page([grocery, salary]))
})

describe('CategoriesPage', () => {
  it('renders the categories with badges and type labels', async () => {
    renderPage()

    expect(await screen.findByText('Food & Groceries')).toBeInTheDocument()
    expect(screen.getByText('Salary')).toBeInTheDocument()
    expect(screen.getAllByText('Default').length).toBe(2)
    expect(screen.getByText('Excluded from budgets')).toBeInTheDocument()
    expect(screen.getAllByText('Expense').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Income').length).toBeGreaterThan(0)
  })

  it('shows an empty state when no categories match', async () => {
    vi.mocked(categoriesApi.list).mockResolvedValue(page([]))
    renderPage()

    expect(
      await screen.findByText('No categories match these filters yet.'),
    ).toBeInTheDocument()
  })

  it('shows an error with a retry action', async () => {
    vi.mocked(categoriesApi.list).mockRejectedValue(new ApiError(500, null, 'Down'))
    renderPage()

    expect(await screen.findByText('Down')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(categoriesApi.list).toHaveBeenCalledTimes(2))
  })

  it('opens the create modal', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: 'New category' }))

    const dialog = await screen.findByRole('dialog', { name: 'New category' })
    expect(dialog).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Name')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Type')).toBeInTheDocument()
  })

  it('filters by type and status', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Food & Groceries')

    await user.selectOptions(screen.getByLabelText('Type'), 'INCOME')
    await user.selectOptions(screen.getByLabelText('Status'), 'ACTIVE')

    await waitFor(() => expect(categoriesApi.list).toHaveBeenCalled())
    const lastCall = vi.mocked(categoriesApi.list).mock.calls.at(-1)
    expect(lastCall?.[0]).toMatchObject({ type: 'INCOME', status: 'ACTIVE' })
  })

  it('searches categories after the debounce', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Food & Groceries')

    await user.type(screen.getByLabelText('Search'), 'food')

    await waitFor(
      () => {
        const lastCall = vi.mocked(categoriesApi.list).mock.calls.at(-1)
        expect(lastCall?.[0]).toMatchObject({ q: 'food' })
      },
      { timeout: 1000 },
    )
  })

  it('archives an active category', async () => {
    const user = userEvent.setup()
    vi.mocked(categoriesApi.list).mockResolvedValue(page([grocery]))
    vi.mocked(categoriesApi.archive).mockResolvedValue({ ...grocery, status: 'ARCHIVED' })
    renderPage()
    await screen.findByText('Food & Groceries')

    await user.click(screen.getByRole('button', { name: 'Archive' }))

    expect(await screen.findByText('Category archived')).toBeInTheDocument()
    expect(categoriesApi.archive).toHaveBeenCalledWith(9)
  })

  it('restores an archived category', async () => {
    const user = userEvent.setup()
    vi.mocked(categoriesApi.list).mockResolvedValue(page([oldCategory]))
    vi.mocked(categoriesApi.restore).mockResolvedValue(grocery)
    renderPage()
    await screen.findByText('Food & Groceries')

    await user.click(screen.getByRole('button', { name: 'Restore' }))

    expect(await screen.findByText('Category restored')).toBeInTheDocument()
    expect(categoriesApi.restore).toHaveBeenCalledWith(3)
  })

  it('confirms before deleting a category', async () => {
    const user = userEvent.setup()
    vi.mocked(categoriesApi.list).mockResolvedValue(page([grocery]))
    vi.mocked(categoriesApi.remove).mockResolvedValue(undefined)
    renderPage()
    await screen.findByText('Food & Groceries')

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete category' })
    expect(dialog).toHaveTextContent('Food & Groceries')

    await user.click(screen.getByRole('button', { name: 'Delete category' }))

    expect(await screen.findByText('Category deleted')).toBeInTheDocument()
    expect(categoriesApi.remove).toHaveBeenCalledWith(9)
  })
})