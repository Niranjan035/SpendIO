import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { analyticsApi } from '../api'
import type { BudgetUtilizationItem } from '../types'
import { BudgetUtilizationCard } from './BudgetUtilizationCard'

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

const item: BudgetUtilizationItem = {
  budgetId: 3,
  name: 'October total',
  spentMinor: 20000,
  amountMinor: 100000,
  utilisationPct: 20,
  remainingMinor: 80000,
  projectedMinor: 60000,
  status: 'ON_TRACK',
  currencyCode: 'EUR',
}

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <BudgetUtilizationCard />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('BudgetUtilizationCard', () => {
  it('formats figures in each budget’s own currency', async () => {
    vi.mocked(analyticsApi.budgetUtilization).mockResolvedValue([item])
    renderCard()

    expect(await screen.findByText('October total')).toBeInTheDocument()
    expect(screen.getByText('€200.00 of €1,000.00')).toBeInTheDocument()
    expect(screen.queryByText('$200.00')).not.toBeInTheDocument()
  })

  it('shows an empty state when there are no active budgets', async () => {
    vi.mocked(analyticsApi.budgetUtilization).mockResolvedValue([])
    renderCard()

    expect(await screen.findByText('No active budgets.')).toBeInTheDocument()
  })
})