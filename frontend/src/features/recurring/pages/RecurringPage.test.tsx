import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '@/app/providers/ToastProvider'
import { transactionsApi } from '@/features/transactions/api'
import type { TransactionFilterOptions } from '@/features/transactions/types'
import { recurringApi } from '../api'
import { RecurringPage } from './RecurringPage'
import type { RecurringTransaction } from '../types'

vi.mock('../api', () => ({
  recurringApi: {
    list: vi.fn(),
    create: vi.fn(),
    upcoming: vi.fn(),
    detail: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    cancel: vi.fn(),
    runNow: vi.fn(),
    preview: vi.fn(),
    runs: vi.fn(),
  },
}))

vi.mock('@/features/transactions/api', () => ({
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

const rule: RecurringTransaction = {
  id: 7,
  account: { id: 1, name: 'Checking', currencyCode: 'USD' },
  toAccount: null,
  category: { id: 9, name: 'Rent', type: 'EXPENSE', color: null, icon: null },
  type: 'EXPENSE',
  direction: 'OUTFLOW',
  amountMinor: 123400,
  currencyCode: 'USD',
  baseAmountMinor: 123400,
  description: 'Rent',
  notes: null,
  frequency: 'MONTHLY',
  intervalCount: 1,
  dayOfMonth: 1,
  dayOfWeek: null,
  skipWeekends: false,
  startDate: '2026-03-01',
  endDate: null,
  targetOccurrenceDate: null,
  nextRunDate: '2026-04-01',
  lastRunDate: '2026-03-01',
  status: 'ACTIVE',
  autoPost: true,
  maxRetries: 3,
  retryCount: 0,
  lastError: null,
  failureReason: null,
  generatedCount: 2,
  createdAt: '2026-03-01T09:00:00Z',
}

const page = {
  content: [rule],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/recurring']}>
          <RecurringPage />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(recurringApi.list).mockResolvedValue(page)
  const options: TransactionFilterOptions = {
    accounts: [{ id: 1, name: 'Checking', currencyCode: 'USD', status: 'ACTIVE' }],
    categories: [],
    currencies: ['USD'],
    minDate: null,
    maxDate: null,
    typeCounts: {},
  }
  vi.mocked(transactionsApi.options).mockResolvedValue(options)
})

describe('RecurringPage', () => {
  it('lists the rules with their next occurrence', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { name: /Rent/ })).toBeInTheDocument()
    expect(screen.getByText('$1,234.00')).toBeInTheDocument()
    expect(screen.getByText(/Every month/)).toBeInTheDocument()
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0)
  })

  it('opens the create modal from the header action', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: 'New rule' }))

    const dialog = await screen.findByRole('dialog', { name: 'New recurring rule' })
    expect(dialog).toBeInTheDocument()
    expect(screen.getByLabelText('Account')).toBeInTheDocument()
    expect(screen.getByLabelText('Amount')).toBeInTheDocument()
  })

  it('refilters the list by status', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findAllByText('Rent')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by status' }), 'PAUSED')

    await waitFor(() =>
      expect(recurringApi.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'PAUSED' }),
      ),
    )
  })
})