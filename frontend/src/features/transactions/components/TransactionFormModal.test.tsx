import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { transactionsApi } from '../api'
import type { Transaction, TransactionFilterOptions } from '../types'
import { TransactionFormModal } from './TransactionFormModal'

vi.mock('../api', () => ({
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

const options: TransactionFilterOptions = {
  accounts: [{ id: 1, name: 'Checking', currencyCode: 'USD', status: 'ACTIVE' }],
  categories: [
    {
      id: 9,
      userId: 1,
      parentId: null,
      name: 'Groceries',
      type: 'EXPENSE',
      icon: null,
      color: null,
      isDefault: false,
      isSystem: false,
      sortOrder: 0,
      excludeFromBudgets: false,
      status: 'ACTIVE',
      archivedAt: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 10,
      userId: 1,
      parentId: null,
      name: 'Salary',
      type: 'INCOME',
      icon: null,
      color: null,
      isDefault: false,
      isSystem: false,
      sortOrder: 0,
      excludeFromBudgets: false,
      status: 'ACTIVE',
      archivedAt: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 11,
      userId: 1,
      parentId: null,
      name: 'Old bonus',
      type: 'INCOME',
      icon: null,
      color: null,
      isDefault: false,
      isSystem: false,
      sortOrder: 0,
      excludeFromBudgets: false,
      status: 'ARCHIVED',
      archivedAt: '2026-02-01T00:00:00Z',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-02-01T00:00:00Z',
    },
  ],
  currencies: ['USD', 'EUR'],
  minDate: '2026-01-01',
  maxDate: '2026-12-31',
  typeCounts: { EXPENSE: 1, INCOME: 1 },
}

function renderModal(transaction: Transaction | null = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <ToastProvider>
          <TransactionFormModal transaction={transaction} open onClose={() => undefined} />
        </ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(transactionsApi.options).mockResolvedValue(options)
})

describe('TransactionFormModal category dropdown', () => {
  it('offers active categories matching the transaction type', async () => {
    renderModal()

    await screen.findByText('Groceries')
    const category = screen.getByLabelText('Category')
    expect(within(category).getByText('Groceries')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Type'), 'INCOME')

    expect(within(category).getByText('Salary')).toBeInTheDocument()
    expect(within(category).queryByText('Groceries')).not.toBeInTheDocument()
    expect(within(category).queryByText('Old bonus')).not.toBeInTheDocument()
  })

  it('hides the category dropdown for adjustments', async () => {
    renderModal()

    await screen.findByLabelText('Category')
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'ADJUSTMENT')

    expect(screen.queryByLabelText('Category')).not.toBeInTheDocument()
  })

  it('keeps the transaction’s own category selectable when editing', async () => {
    const transaction: Transaction = {
      id: 7,
      account: { id: 1, name: 'Checking', currencyCode: 'USD' },
      category: { id: 99, name: 'Legacy income', type: 'INCOME', color: null, icon: null },
      type: 'INCOME',
      direction: 'INFLOW',
      amountMinor: 1000,
      currencyCode: 'USD',
      exchangeRate: 1,
      baseAmountMinor: 1000,
      baseCurrencyCode: 'USD',
      occurredOn: '2026-03-01',
      description: null,
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
    renderModal(transaction)

    await screen.findByText('Salary')
    expect(within(screen.getByLabelText('Category')).getByText('Legacy income')).toBeInTheDocument()
  })

  it('shows a loading state while the options fetch is pending', async () => {
    let resolveOptions!: (value: TransactionFilterOptions) => void
    vi.mocked(transactionsApi.options).mockImplementation(
      () =>
        new Promise<TransactionFilterOptions>((res) => {
          resolveOptions = res
        }),
    )
    renderModal()

    const category = screen.getByLabelText('Category')
    expect(category).toBeDisabled()
    expect(within(category).getByText('Loading categories…')).toBeInTheDocument()

    resolveOptions(options)
    await screen.findByText('Groceries')
  })

  it('shows an empty state with a link to category management', async () => {
    vi.mocked(transactionsApi.options).mockResolvedValue({
      ...options,
      categories: options.categories.filter((category) => category.type === 'EXPENSE'),
    })
    renderModal()

    await userEvent.selectOptions(await screen.findByLabelText('Type'), 'INCOME')

    expect(
      await screen.findByText(/No income categories yet/),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Add one' })).toHaveAttribute('href', '/categories')
  })

  it('shows an error and recovers via Try again', async () => {
    vi.mocked(transactionsApi.options)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue(options)
    renderModal()

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load categories.')

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Groceries')).toBeInTheDocument()
  })

  it('submits the selected category id as a number', async () => {
    vi.mocked(transactionsApi.create).mockResolvedValue({
      id: 20,
      account: { id: 1, name: 'Checking', currencyCode: 'USD' },
      category: { id: 10, name: 'Salary', type: 'INCOME', color: null, icon: null },
      type: 'INCOME',
      direction: 'INFLOW',
      amountMinor: 10000,
      currencyCode: 'USD',
      exchangeRate: 1,
      baseAmountMinor: 10000,
      baseCurrencyCode: 'USD',
      occurredOn: '2026-03-05',
      description: null,
      merchant: null,
      payee: null,
      notes: null,
      status: 'POSTED',
      transferGroupId: null,
      reversalOfTransactionId: null,
      reversedAt: null,
      recurringTransactionId: null,
      csvImportId: null,
      createdAt: '2026-03-05T10:00:00Z',
      updatedAt: '2026-03-05T10:00:00Z',
      version: 0,
    })
    renderModal()

    await screen.findByText('Checking')
    await userEvent.selectOptions(screen.getByLabelText('Account'), '1')
    await userEvent.selectOptions(screen.getByLabelText('Type'), 'INCOME')
    await userEvent.selectOptions(screen.getByLabelText('Category'), '10')
    await userEvent.type(screen.getByLabelText('Amount'), '100')
    await userEvent.click(screen.getByRole('button', { name: 'Add transaction' }))

    await waitFor(() => expect(transactionsApi.create).toHaveBeenCalledOnce())
    const [input] = vi.mocked(transactionsApi.create).mock.calls[0]!
    expect(input.categoryId).toBe(10)
    expect(typeof input.categoryId).toBe('number')
    expect(input.type).toBe('INCOME')
    expect(input.accountId).toBe(1)
    expect(input.amountMinor).toBe(10000)
  })
})