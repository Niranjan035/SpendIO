import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider } from '@/app/providers/ThemeProvider'
import { useAuthStore } from '@/features/auth/auth.store'
import { analyticsApi } from '../api'
import { AnalyticsPage } from './AnalyticsPage'

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

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <AnalyticsPage />
      </QueryClientProvider>
    </ThemeProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(analyticsApi.cashFlow).mockResolvedValue([])
  vi.mocked(analyticsApi.spendingTrend).mockResolvedValue([])
  vi.mocked(analyticsApi.incomeVsExpense).mockResolvedValue([])
  vi.mocked(analyticsApi.categoryBreakdown).mockResolvedValue([])
  vi.mocked(analyticsApi.monthlyComparison).mockResolvedValue([])
  vi.mocked(analyticsApi.netWorth).mockResolvedValue([])
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

afterEach(() => {
  useAuthStore.setState({ status: 'anonymous', user: null, hydrated: true })
})

describe('AnalyticsPage display currency', () => {
  it('labels overview amounts with the user’s preferred currency', async () => {
    useAuthStore.setState({
      status: 'authenticated',
      user: {
        id: '42',
        email: 'ana@example.com',
        displayName: 'Ana',
        baseCurrency: 'INR',
      },
      hydrated: true,
    })
    renderPage()

    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText('₹1,234.56')).toBeInTheDocument()
  })

  it('falls back to USD when the session has no currency', async () => {
    renderPage()

    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText('$1,234.56')).toBeInTheDocument()
  })
})