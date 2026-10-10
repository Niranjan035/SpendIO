import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { toIsoDate } from '@/lib/dates'
import { transactionsApi, transfersApi } from '../api'
import { ReverseTransactionDialog } from './ReverseTransactionDialog'
import type { Transaction } from '../types'

vi.mock('../api', () => ({
  transactionsApi: {
    reverse: vi.fn(),
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

const transferLeg: Transaction = {
  ...transaction,
  type: 'TRANSFER',
  category: null,
  transferGroupId: 'g-1',
}

const mirror: Transaction = { ...transaction, id: 8, reversalOfTransactionId: 7 }

function renderDialog(transactionProp: Transaction | null, onClose = vi.fn()) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <ReverseTransactionDialog transaction={transactionProp} open onClose={onClose} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return onClose
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(transactionsApi.reverse).mockResolvedValue(mirror)
  vi.mocked(transfersApi.reverse).mockResolvedValue({
    transferGroupId: 'g-1',
    fromTransaction: transaction,
    toTransaction: { ...transaction, id: 9, direction: 'INFLOW' },
  })
})

describe('ReverseTransactionDialog', () => {
  it('reverses a single row through the mirror endpoint', async () => {
    const onClose = renderDialog(transaction)
    const user = userEvent.setup()

    expect(screen.getByRole('dialog', { name: 'Reverse transaction' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Reason'), 'Duplicate charge')
    await user.click(screen.getByRole('button', { name: 'Reverse' }))

    await waitFor(() =>
      expect(transactionsApi.reverse).toHaveBeenCalledWith(7, {
        reason: 'Duplicate charge',
        occurredOn: toIsoDate(new Date()),
      }),
    )
    expect(transfersApi.reverse).not.toHaveBeenCalled()
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('routes a transfer leg to the whole-group endpoint', async () => {
    const onClose = renderDialog(transferLeg)
    const user = userEvent.setup()

    expect(screen.getByRole('dialog', { name: 'Reverse transfer' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Reason'), 'Wrong accounts')
    await user.click(screen.getByRole('button', { name: 'Reverse' }))

    await waitFor(() =>
      expect(transfersApi.reverse).toHaveBeenCalledWith('g-1', {
        reason: 'Wrong accounts',
        occurredOn: toIsoDate(new Date()),
      }),
    )
    expect(transactionsApi.reverse).not.toHaveBeenCalled()
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('requires a reason before calling the API', async () => {
    const onClose = renderDialog(transaction)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Reverse' }))

    expect(await screen.findByText('Enter a reason')).toBeInTheDocument()
    expect(transactionsApi.reverse).not.toHaveBeenCalled()
    expect(transfersApi.reverse).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('summarises the row being reversed', () => {
    renderDialog(transaction)

    expect(screen.getByText('Coffee beans')).toBeInTheDocument()
    expect(screen.getByText(/Roast House/)).toBeInTheDocument()
    expect(screen.getByText(/\$12\.34/)).toBeInTheDocument()
  })
})
