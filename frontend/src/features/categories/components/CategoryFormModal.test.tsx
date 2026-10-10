import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { ApiError } from '@/services/http/ApiError'
import { categoriesApi } from '../api'
import type { Category } from '../types'
import { CategoryFormModal } from './CategoryFormModal'

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

const category: Category = {
  id: 9,
  userId: 1,
  parentId: null,
  name: 'Food & Groceries',
  type: 'EXPENSE',
  icon: 'utensils',
  color: '#16a34a',
  isDefault: false,
  isSystem: false,
  sortOrder: 0,
  excludeFromBudgets: false,
  status: 'ACTIVE',
  archivedAt: null,
  createdAt: '2026-01-01T09:00:00Z',
  updatedAt: '2026-01-01T09:00:00Z',
}

function renderModal(categoryValue: Category | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const onClose = vi.fn()
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <CategoryFormModal category={categoryValue} open onClose={onClose} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return onClose
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('CategoryFormModal', () => {
  it('creates a category, sending null for blank icon and colour', async () => {
    const user = userEvent.setup()
    vi.mocked(categoriesApi.create).mockResolvedValue(category)
    const onClose = renderModal()

    await user.type(screen.getByLabelText('Name'), 'Food & Groceries')
    await user.click(screen.getByLabelText('Exclude transactions in this category from budget calculations'))
    await user.click(screen.getByRole('button', { name: 'Create category' }))

    expect(await screen.findByText('Category created')).toBeInTheDocument()
    expect(categoriesApi.create).toHaveBeenCalledWith({
      name: 'Food & Groceries',
      type: 'EXPENSE',
      icon: null,
      color: null,
      excludeFromBudgets: true,
    })
    expect(onClose).toHaveBeenCalled()
  })

  it('sends the chosen type when creating an income category', async () => {
    const user = userEvent.setup()
    vi.mocked(categoriesApi.create).mockResolvedValue({ ...category, type: 'INCOME' })
    renderModal()

    await user.selectOptions(screen.getByLabelText('Type'), 'INCOME')
    await user.type(screen.getByLabelText('Name'), 'Salary')
    await user.click(screen.getByRole('button', { name: 'Create category' }))

    expect(await screen.findByText('Category created')).toBeInTheDocument()
    expect(categoriesApi.create).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Salary', type: 'INCOME' }),
    )
  })

  it('surfaces backend field errors on the form without a generic toast', async () => {
    const user = userEvent.setup()
    vi.mocked(categoriesApi.create).mockRejectedValue(
      new ApiError(400, {
        status: 400,
        code: 'VALIDATION_ERROR',
        detail: 'Request validation failed',
        fieldErrors: [{ field: 'name', message: 'a category with this name already exists' }],
      }),
    )
    renderModal()

    await user.type(screen.getByLabelText('Name'), 'Food')
    await user.click(screen.getByRole('button', { name: 'Create category' }))

    expect(await screen.findByText('a category with this name already exists')).toBeInTheDocument()
    expect(screen.queryByText('Request validation failed')).not.toBeInTheDocument()
  })

  it('blocks submit when the client schema rejects a blank name', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.click(screen.getByRole('button', { name: 'Create category' }))

    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(categoriesApi.create).not.toHaveBeenCalled()
  })

  it('edits an existing category without exposing the type selector', async () => {
    const user = userEvent.setup()
    vi.mocked(categoriesApi.update).mockResolvedValue(category)
    renderModal(category)

    expect(screen.queryByLabelText('Type')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('Food & Groceries')

    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Groceries')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Category updated')).toBeInTheDocument()
    expect(categoriesApi.update).toHaveBeenCalledWith(9, {
      name: 'Groceries',
      icon: 'utensils',
      color: '#16a34a',
      excludeFromBudgets: false,
    })
  })
})