import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { analyticsApi } from '../api'
import type { GoalProgressItem } from '../types'
import { GoalProgressCard } from './GoalProgressCard'

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

const item: GoalProgressItem = {
  goalId: 4,
  name: 'Emergency fund',
  targetMinor: 1000000,
  currentMinor: 500000,
  progressPct: 50,
  onTrack: true,
  currencyCode: 'INR',
}

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <GoalProgressCard />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('GoalProgressCard', () => {
  it('formats figures in each goal’s own currency', async () => {
    vi.mocked(analyticsApi.goalProgress).mockResolvedValue([item])
    renderCard()

    expect(await screen.findByText('Emergency fund')).toBeInTheDocument()
    expect(screen.getByText('₹5,000.00 of ₹10,000.00')).toBeInTheDocument()
    expect(screen.queryByText('$5,000.00')).not.toBeInTheDocument()
  })

  it('shows an empty state when there are no active goals', async () => {
    vi.mocked(analyticsApi.goalProgress).mockResolvedValue([])
    renderCard()

    expect(await screen.findByText('No active goals.')).toBeInTheDocument()
  })
})