import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { accountsApi } from '../api'
import { AccountsPage } from './AccountsPage'

vi.mock('../api', () => ({
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

const page = {
  content: [
    {
      id: 7,
      name: 'Everyday checking',
      type: 'BANK' as const,
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
      status: 'ACTIVE' as const,
      transactionCount: 3,
      createdAt: '2026-01-01T09:00:00Z',
      balanceMinor: 125000,
      balanceBaseMinor: 125000,
      baseCurrencyCode: 'EUR',
      availableCreditMinor: null,
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

const totals = {
  totalBaseMinor: 125000,
  baseCurrencyCode: 'EUR',
  byType: { BANK: 125000 },
  byCurrency: { EUR: 125000 },
}

function renderAccountsPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/accounts']}>
          <AccountsPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(accountsApi.list).mockResolvedValue(page)
  vi.mocked(accountsApi.summary).mockResolvedValue(totals)
})

describe('AccountsPage', () => {
  it('shows the balance total and the account rows', async () => {
    renderAccountsPage()

    expect(await screen.findByText('Everyday checking')).toBeInTheDocument()
    expect(screen.getAllByText('€1,250.00').length).toBeGreaterThan(0)
    expect(screen.getByText('Primary')).toBeInTheDocument()
    expect(screen.getByText(/3 transactions/)).toBeInTheDocument()
  })

  it('opens the create modal with the new-account form', async () => {
    const user = userEvent.setup()
    renderAccountsPage()

    await user.click(screen.getByRole('button', { name: 'New account' }))

    const dialog = await screen.findByRole('dialog', { name: 'New account' })
    expect(dialog).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toBeInTheDocument()
    expect(screen.getByLabelText('Currency')).toBeInTheDocument()
  })

  it('links each row to its detail page', async () => {
    renderAccountsPage()

    const link = await screen.findByRole('link', { name: /Everyday checking/ })
    expect(link).toHaveAttribute('href', '/accounts/7')
  })
})
