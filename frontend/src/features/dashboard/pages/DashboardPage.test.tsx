import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { DashboardSummary } from '@/features/analytics/types'
import {
  useDashboardSummary,
  useDashboardUpcomingRecurring,
  useRecentTransactions,
} from '@/features/analytics/hooks'
import { DashboardPage } from './DashboardPage'

vi.mock('@/features/analytics/hooks', () => ({
  useDashboardSummary: vi.fn(),
  useRecentTransactions: vi.fn(),
  useDashboardUpcomingRecurring: vi.fn(),
}))

function summary(overrides: Partial<DashboardSummary> = {}): DashboardSummary {
  return {
    totalBalanceBaseMinor: 0,
    baseCurrencyCode: 'USD',
    incomeBaseMinor: 0,
    expenseBaseMinor: 0,
    savingsBaseMinor: 0,
    savingsRatePct: null,
    transactionCount: 0,
    budgetUtilisationPct: 0,
    goalProgressPct: 0,
    periodStart: '2026-10-01',
    periodEnd: '2026-10-31',
    compare: {
      previousIncome: 0,
      previousExpense: 0,
      previousSavings: 0,
      deltas: {},
    },
    ...overrides,
  }
}

function mockSummary(data: DashboardSummary) {
  vi.mocked(useDashboardSummary).mockReturnValue({
    data,
    isLoading: false,
  } as unknown as ReturnType<typeof useDashboardSummary>)
}

function renderDashboard() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockSummary(summary())
  vi.mocked(useRecentTransactions).mockReturnValue({
    data: [],
    isLoading: false,
  } as unknown as ReturnType<typeof useRecentTransactions>)
  vi.mocked(useDashboardUpcomingRecurring).mockReturnValue({
    data: [],
    isLoading: false,
  } as unknown as ReturnType<typeof useDashboardUpcomingRecurring>)
})

describe('DashboardPage', () => {
  it('renders an empty dashboard without crashing', () => {
    renderDashboard()

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('Net worth')).toBeInTheDocument()
    expect(screen.getByText('Income this month')).toBeInTheDocument()
    expect(screen.getByText('Spending this month')).toBeInTheDocument()
    expect(screen.getByText('Savings rate')).toBeInTheDocument()
    expect(screen.getByText('No transactions yet.')).toBeInTheDocument()
    expect(screen.getByText('Nothing scheduled in this window.')).toBeInTheDocument()
    // Every "no comparison" delta and the savings rate render as a placeholder.
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(5)
  })

  it('tolerates delta fields omitted from the payload (NON_NULL)', () => {
    mockSummary(
      summary({
        compare: {
          previousIncome: 500_00,
          previousExpense: 100_00,
          previousSavings: 400_00,
          deltas: {},
        },
      }),
    )

    expect(() => renderDashboard()).not.toThrow()
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(3)
  })

  it('tolerates explicitly null delta fields', () => {
    mockSummary(
      summary({
        compare: {
          previousIncome: 0,
          previousExpense: 0,
          previousSavings: 0,
          deltas: { incomePct: null, expensePct: null, savingsPct: null },
        },
      }),
    )

    expect(() => renderDashboard()).not.toThrow()
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(3)
  })

  it('formats present deltas as signed percentages', () => {
    mockSummary(
      summary({
        budgetUtilisationPct: 25.5,
        goalProgressPct: 60,
        compare: {
          previousIncome: 0,
          previousExpense: 0,
          previousSavings: 0,
          deltas: { incomePct: 12.34, expensePct: -4.5, savingsPct: 0 },
        },
      }),
    )

    renderDashboard()

    expect(screen.getByText('+12.3%')).toBeInTheDocument()
    expect(screen.getByText('-4.5%')).toBeInTheDocument()
    // Savings delta is shown on both the net-worth and savings-rate cards.
    expect(screen.getAllByText('0.0%')).toHaveLength(2)
  })
})
