import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { analyticsApi } from '../api'
import { TopSpendingTable } from './TopSpendingTable'

vi.mock('../api', () => ({
  analyticsApi: {
    cashFlow: vi.fn(),
    spendingTrend: vi.fn(),
    incomeVsExpense: vi.fn(),
    categoryBreakdown: vi.fn(),
    topSpending: vi.fn(),
    monthlyComparison: vi.fn(),
    budgetUtilization: vi.fn(),
    goalProgress: vi.fn(),
    netWorth: vi.fn(),
    accounts: vi.fn(),
    dashboardSummary: vi.fn(),
    recentTransactions: vi.fn(),
    upcomingRecurring: vi.fn(),
  },
}))

function renderTable(currency: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <TopSpendingTable currency={currency} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(analyticsApi.topSpending).mockResolvedValue([
    {
      rank: 1,
      categoryId: 9,
      name: 'Groceries',
      amountMinor: 123456,
      sharePct: 45.2,
      deltaPct: 10.0,
    },
  ])
})

describe('TopSpendingTable', () => {
  it('formats amounts with the display currency', async () => {
    renderTable('INR')
    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText('₹1,234.56')).toBeInTheDocument()
  })

  it('defaults to USD formatting when no currency is provided', async () => {
    renderTable('USD')
    expect(await screen.findByText('$1,234.56')).toBeInTheDocument()
  })

  it('handles currencies with no minor units', async () => {
    vi.mocked(analyticsApi.topSpending).mockResolvedValue([
      {
        rank: 1,
        categoryId: 9,
        name: 'Groceries',
        amountMinor: 1234,
        sharePct: 100,
        deltaPct: null,
      },
    ])
    renderTable('JPY')
    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText((t) => t.includes('1,234') && t.includes('¥'))).toBeInTheDocument()
    expect(screen.queryByText(/1,234\.\d/)).not.toBeInTheDocument()
  })
})