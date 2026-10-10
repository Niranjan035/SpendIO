import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { toIsoDate } from '@/lib/dates'
import { transactionsApi, transfersApi } from '../api'
import { TransferFormModal } from './TransferFormModal'
import type { TransactionFilterOptions, Transfer } from '../types'

vi.mock('../api', () => ({
  transactionsApi: {
    options: vi.fn(),
  },
  transfersApi: {
    create: vi.fn(),
    reverse: vi.fn(),
  },
}))

const options: TransactionFilterOptions = {
  accounts: [
    { id: 1, name: 'Checking', currencyCode: 'USD', status: 'ACTIVE' },
    { id: 2, name: 'Savings', currencyCode: 'USD', status: 'ACTIVE' },
    { id: 3, name: 'Old card', currencyCode: 'USD', status: 'ARCHIVED' },
  ],
  categories: [],
  currencies: ['USD'],
  minDate: null,
  maxDate: null,
  typeCounts: {},
}

const transfer: Transfer = {
  transferGroupId: 'g-1',
  fromTransaction: {
    id: 7,
    account: { id: 1, name: 'Checking', currencyCode: 'USD' },
    category: null,
    type: 'TRANSFER',
    direction: 'OUTFLOW',
    amountMinor: 2000,
    currencyCode: 'USD',
    exchangeRate: 1,
    baseAmountMinor: 2000,
    baseCurrencyCode: 'USD',
    occurredOn: '2026-03-01',
    description: 'Rent share',
    merchant: null,
    payee: null,
    notes: null,
    status: 'POSTED',
    transferGroupId: 'g-1',
    reversalOfTransactionId: null,
    reversedAt: null,
    recurringTransactionId: null,
    csvImportId: null,
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
    version: 0,
  },
  toTransaction: {
    id: 8,
    account: { id: 2, name: 'Savings', currencyCode: 'USD' },
    category: null,
    type: 'TRANSFER',
    direction: 'INFLOW',
    amountMinor: 2000,
    currencyCode: 'USD',
    exchangeRate: 1,
    baseAmountMinor: 2000,
    baseCurrencyCode: 'USD',
    occurredOn: '2026-03-01',
    description: 'Rent share',
    merchant: null,
    payee: null,
    notes: null,
    status: 'POSTED',
    transferGroupId: 'g-1',
    reversalOfTransactionId: null,
    reversedAt: null,
    recurringTransactionId: null,
    csvImportId: null,
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
    version: 0,
  },
}

function renderModal(onClose = vi.fn()) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <TransferFormModal open onClose={onClose} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return onClose
}

async function selectAccounts(user: ReturnType<typeof userEvent.setup>) {
  const dialog = screen.getByRole('dialog', { name: 'New transfer' })
  await waitFor(() => expect(within(dialog).getAllByRole('option').length).toBeGreaterThan(4))
  await user.selectOptions(within(dialog).getByLabelText('From account'), '1')
  await user.selectOptions(within(dialog).getByLabelText('To account'), '2')
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(transactionsApi.options).mockResolvedValue(options)
  vi.mocked(transfersApi.create).mockResolvedValue(transfer)
})

describe('TransferFormModal', () => {
  it('creates a transfer between two active accounts', async () => {
    const onClose = renderModal()
    const user = userEvent.setup()
    await selectAccounts(user)

    await user.type(screen.getByLabelText('Amount'), '20.00')
    await user.type(screen.getByLabelText('Description'), 'Rent share')
    await user.click(screen.getByRole('button', { name: 'Create transfer' }))

    await waitFor(() =>
      expect(transfersApi.create).toHaveBeenCalledWith({
        fromAccountId: 1,
        toAccountId: 2,
        amountMinor: 2000,
        currencyCode: 'USD',
        toAmountMinor: null,
        occurredOn: toIsoDate(new Date()),
        description: 'Rent share',
        notes: null,
      }),
    )
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('hides archived accounts from both selects', async () => {
    renderModal()

    const dialog = screen.getByRole('dialog', { name: 'New transfer' })
    await waitFor(() => expect(within(dialog).getAllByRole('option').length).toBeGreaterThan(4))
    expect(within(dialog).queryByRole('option', { name: 'Old card' })).not.toBeInTheDocument()
  })

  it('refuses the same source and destination account', async () => {
    const onClose = renderModal()
    const user = userEvent.setup()
    const dialog = screen.getByRole('dialog', { name: 'New transfer' })
    await waitFor(() => expect(within(dialog).getAllByRole('option').length).toBeGreaterThan(4))
    await user.selectOptions(within(dialog).getByLabelText('From account'), '1')
    await user.selectOptions(within(dialog).getByLabelText('To account'), '1')
    await user.type(within(dialog).getByLabelText('Amount'), '5.00')

    await user.click(within(dialog).getByRole('button', { name: 'Create transfer' }))

    expect(await screen.findByText('Pick a different destination account')).toBeInTheDocument()
    expect(transfersApi.create).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('requires a positive amount', async () => {
    renderModal()
    const user = userEvent.setup()
    await selectAccounts(user)

    await user.click(screen.getByRole('button', { name: 'Create transfer' }))

    expect(await screen.findByText('Enter a positive amount')).toBeInTheDocument()
    expect(transfersApi.create).not.toHaveBeenCalled()
  })
})
