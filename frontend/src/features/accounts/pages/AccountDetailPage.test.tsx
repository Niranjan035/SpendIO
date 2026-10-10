import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { accountsApi } from '../api'
import { AccountDetailPage } from './AccountDetailPage'

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

const account = {
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
}

const balance = {
  accountId: 7,
  currencyCode: 'EUR',
  balanceMinor: 125000,
  baseCurrencyCode: 'EUR',
  balanceBaseMinor: 125000,
  asOf: '2026-03-01',
  availableCreditMinor: null,
}

function renderDetailPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/accounts/7']}>
          <Routes>
            <Route path="/accounts/:id" element={<AccountDetailPage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(accountsApi.detail).mockResolvedValue(account)
  vi.mocked(accountsApi.balance).mockResolvedValue(balance)
  vi.mocked(accountsApi.balances).mockResolvedValue([
    { date: '2026-03-01', balanceMinor: 125000, balanceBaseMinor: 125000 },
    { date: '2026-02-28', balanceMinor: 120000, balanceBaseMinor: 120000 },
  ])
})

describe('AccountDetailPage', () => {
  it('renders the balance, details and the daily series', async () => {
    renderDetailPage()

    expect(await screen.findByRole('heading', { name: 'Everyday checking' })).toBeInTheDocument()
    expect(screen.getAllByText('€1,250.00').length).toBeGreaterThan(0)
    expect(screen.getByText('01 Mar 2026')).toBeInTheDocument()
    expect(screen.getByText('28 Feb 2026')).toBeInTheDocument()
    expect(screen.getByText(/Bank account · Revolut/)).toBeInTheDocument()
  })

  it('does not offer make-primary when the account is already primary', async () => {
    renderDetailPage()

    await screen.findByRole('heading', { name: 'Everyday checking' })
    expect(screen.queryByRole('button', { name: 'Make primary' })).not.toBeInTheDocument()
  })

  it('submits an adjustment with the parsed minor balance and a reason', async () => {
    vi.mocked(accountsApi.adjust).mockResolvedValue({
      ...balance,
      balanceMinor: 60000,
      balanceBaseMinor: 60000,
      asOf: '2026-03-02',
    })
    const user = userEvent.setup()
    renderDetailPage()

    await screen.findByRole('heading', { name: 'Everyday checking' })
    const amount = screen.getByLabelText('New balance')
    await user.clear(amount)
    await user.type(amount, '600.00')
    await user.type(screen.getByLabelText('Reason'), 'cash check')

    await user.click(screen.getByRole('button', { name: 'Adjust balance' }))

    expect(await screen.findByText('Balance adjusted')).toBeInTheDocument()
    expect(accountsApi.adjust).toHaveBeenCalledWith(7, {
      newBalanceMinor: 60000,
      occurredOn: expect.any(String),
      reason: 'cash check',
    })
  })

  it('enters a force-delete confirmation when the account has transactions', async () => {
    const user = userEvent.setup()
    renderDetailPage()

    await user.click(await screen.findByRole('button', { name: 'Delete' }))

    const dialog = await screen.findByRole('dialog', { name: 'Delete account' })
    expect(dialog).toHaveTextContent('3 transactions')
    expect(accountsApi.remove).not.toHaveBeenCalled()
  })

  it('reflects a saved name edit on the detail page', async () => {
    const user = userEvent.setup()
    let current = { ...account }
    vi.mocked(accountsApi.detail).mockImplementation(async () => current)
    vi.mocked(accountsApi.update).mockImplementation(async (_id, input) => {
      current = { ...current, name: input.name ?? current.name }
      return current
    })
    renderDetailPage()

    await screen.findByRole('heading', { name: 'Everyday checking' })
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const name = await screen.findByLabelText('Name')
    await user.clear(name)
    await user.type(name, 'Renamed checking')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Account updated')).toBeInTheDocument()
    expect(accountsApi.update).toHaveBeenCalledWith(7, {
      name: 'Renamed checking',
      institution: 'Revolut',
      last4: null,
      color: null,
      notes: null,
    })
    expect(await screen.findByRole('heading', { name: 'Renamed checking' })).toBeInTheDocument()
  })
})
