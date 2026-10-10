import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { transactionsApi } from '../api'
import { TransactionsPage } from './TransactionsPage'
import type { Transaction, TransactionFilterOptions, TransactionSummary } from '../types'

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
  merchant: 'Roast House',
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

const page = {
  content: [transaction],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

const summary: TransactionSummary = {
  count: 1,
  incomeBaseMinor: 0,
  expenseBaseMinor: 1234,
  netBaseMinor: -1234,
  baseCurrencyCode: 'USD',
}

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
  ],
  currencies: ['USD', 'EUR'],
  minDate: '2026-01-01',
  maxDate: '2026-12-31',
  typeCounts: { EXPENSE: 1 },
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.search}</output>
}

function renderTransactionsPage(initialEntry = '/transactions') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <TransactionsPage />
          <LocationProbe />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(transactionsApi.list).mockResolvedValue(page)
  vi.mocked(transactionsApi.summary).mockResolvedValue(summary)
  vi.mocked(transactionsApi.options).mockResolvedValue(options)
})

describe('TransactionsPage', () => {
  it('shows the filtered rows and the summary totals', async () => {
    renderTransactionsPage()

    expect(await screen.findByText('Coffee beans')).toBeInTheDocument()
    expect(screen.getAllByText('Groceries').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/\$12\.34/).length).toBeGreaterThan(0)
    expect(screen.getByText('Matching rows')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Coffee beans' })).toBeInTheDocument()
  })

  it('applies the filter carried in the URL', async () => {
    renderTransactionsPage('/transactions?type=EXPENSE&accountId=1')

    await waitFor(() =>
      expect(transactionsApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ types: ['EXPENSE'], accountIds: [1] }),
      ),
    )
    expect(await screen.findByText('Coffee beans')).toBeInTheDocument()
  })

  it('writes filter changes back to the URL', async () => {
    const user = userEvent.setup()
    renderTransactionsPage()

    await screen.findByText('Coffee beans')
    await user.selectOptions(screen.getByLabelText('Type'), 'INCOME')

    await waitFor(() => expect(screen.getByTestId('location').textContent).toContain('type=INCOME'))
    await waitFor(() =>
      expect(transactionsApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ types: ['INCOME'] }),
      ),
    )
  })

  it('debounces the search box into the query', async () => {
    const user = userEvent.setup()
    renderTransactionsPage()

    await screen.findByText('Coffee beans')
    await user.type(screen.getByLabelText('Search'), 'beans')

    await waitFor(() =>
      expect(transactionsApi.list).toHaveBeenCalledWith(expect.objectContaining({ q: 'beans' })),
    )
    await waitFor(() => expect(screen.getByTestId('location').textContent).toContain('q=beans'))
  })

  it('opens the create modal with the transaction form', async () => {
    const user = userEvent.setup()
    renderTransactionsPage()

    await user.click(await screen.findByRole('button', { name: 'New transaction' }))

    const dialog = await screen.findByRole('dialog', { name: 'New transaction' })
    expect(within(dialog).getByLabelText('Account')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Amount')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Date')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Add transaction' })).toBeInTheDocument()
  })

  it('opens the edit modal prefilled from the row', async () => {
    const user = userEvent.setup()
    renderTransactionsPage()

    await user.click(await screen.findByRole('button', { name: 'Edit Coffee beans' }))

    const dialog = await screen.findByRole('dialog', { name: 'Edit transaction' })
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Description')).toHaveValue('Coffee beans'),
    )
    expect(within(dialog).getByLabelText('Merchant')).toHaveValue('Roast House')
    expect(within(dialog).getByRole('button', { name: 'Save changes' })).toBeInTheDocument()
  })

  it('opens the transfer modal from the header', async () => {
    const user = userEvent.setup()
    renderTransactionsPage()

    await user.click(await screen.findByRole('button', { name: 'New transfer' }))

    const dialog = await screen.findByRole('dialog', { name: 'New transfer' })
    expect(within(dialog).getByLabelText('From account')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('To account')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Amount')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Create transfer' })).toBeInTheDocument()
  })

  it('opens the reverse dialog from the row action', async () => {
    const user = userEvent.setup()
    renderTransactionsPage()

    await user.click(await screen.findByRole('button', { name: 'Reverse Coffee beans' }))

    const dialog = await screen.findByRole('dialog', { name: 'Reverse transaction' })
    expect(within(dialog).getByLabelText('Reason')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Reversal date')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Reverse' })).toBeInTheDocument()
  })

  it('locks reversed and mirrored rows out of editing and reversing', async () => {
    vi.mocked(transactionsApi.list).mockResolvedValue({
      ...page,
      content: [
        { ...transaction, reversedAt: '2026-03-02T12:00:00Z' },
        { ...transaction, id: 8, description: 'Mirror row', reversalOfTransactionId: 7 },
      ],
    })
    renderTransactionsPage()

    await screen.findByText('Coffee beans')
    await screen.findByText('Mirror row')

    expect(screen.queryByRole('button', { name: 'Edit Coffee beans' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reverse Coffee beans' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit Mirror row' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reverse Mirror row' })).not.toBeInTheDocument()
  })

  it('offers group reversal on a transfer leg instead of editing', async () => {
    vi.mocked(transactionsApi.list).mockResolvedValue({
      ...page,
      content: [{ ...transaction, type: 'TRANSFER', category: null, transferGroupId: 'g-1' }],
    })
    const user = userEvent.setup()
    renderTransactionsPage()

    await screen.findByText('Coffee beans')
    expect(screen.queryByRole('button', { name: 'Edit Coffee beans' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reverse Coffee beans' }))

    const dialog = await screen.findByRole('dialog', { name: 'Reverse transfer' })
    expect(within(dialog).getByLabelText('Reason')).toBeInTheDocument()
  })
})
