import { useState, type ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { useDisplayCurrency } from '../currency'
import { CashFlowCard } from '../components/CashFlowCard'
import { SpendingTrendCard } from '../components/SpendingTrendCard'
import { IncomeVsExpenseCard } from '../components/IncomeVsExpenseCard'
import { CategoryBreakdownCard } from '../components/CategoryBreakdownCard'
import { TopSpendingTable } from '../components/TopSpendingTable'
import { BudgetUtilizationCard } from '../components/BudgetUtilizationCard'
import { GoalProgressCard } from '../components/GoalProgressCard'
import { NetWorthCard } from '../components/NetWorthCard'
import { AccountBreakdownCard } from '../components/AccountBreakdownCard'
import { MonthlyComparisonCard } from '../components/MonthlyComparisonCard'

const TABS: ReadonlyArray<{ id: string; label: string }> = [
  { id: 'overview', label: 'Overview' },
  { id: 'spending', label: 'Spending trend' },
  { id: 'categories', label: 'Categories' },
  { id: 'networth', label: 'Net worth' },
  { id: 'comparison', label: 'Comparison' },
  { id: 'budgets-goals', label: 'Budgets & goals' },
]

export function AnalyticsPage() {
  const [tab, setTab] = useState('overview')
  const currency = useDisplayCurrency()

  /** Base-currency analytics are labelled with the user's display currency. */
  const panels: Record<string, ReactNode> = {
    overview: (
      <div className="flex flex-col gap-6">
        <CashFlowCard currency={currency} />
        <div className="grid gap-6 xl:grid-cols-2">
          <IncomeVsExpenseCard currency={currency} />
          <TopSpendingTable currency={currency} />
        </div>
      </div>
    ),
    spending: (
      <div className="flex flex-col gap-6">
        <SpendingTrendCard currency={currency} />
      </div>
    ),
    categories: (
      <div className="flex flex-col gap-6">
        <CategoryBreakdownCard currency={currency} />
      </div>
    ),
    networth: (
      <div className="flex flex-col gap-6">
        <NetWorthCard currency={currency} />
        <AccountBreakdownCard />
      </div>
    ),
    comparison: (
      <div className="flex flex-col gap-6">
        <MonthlyComparisonCard currency={currency} />
      </div>
    ),
    'budgets-goals': (
      <div className="grid gap-6 xl:grid-cols-2">
        <BudgetUtilizationCard />
        <GoalProgressCard />
      </div>
    ),
  }

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold text-text">Analytics</h1>
        <p className="mt-1 text-sm text-muted">Trends, category breakdowns, cash flow and net worth.</p>
      </header>

      <div
        role="tablist"
        aria-label="Analytics views"
        className="flex flex-wrap gap-1 border-b border-border"
      >
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            role="tab"
            id={`analytics-tab-${id}`}
            aria-selected={tab === id}
            aria-controls={`analytics-panel-${id}`}
            onClick={() => setTab(id)}
            className={cn(
              '-mb-px rounded-t-md border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              tab === id
                ? 'border-primary text-primary'
                : 'border-transparent text-muted hover:bg-surface-sunken hover:text-text',
            )}
          >
            {label}
          </button>
        ))}
      </div>

      <section
        role="tabpanel"
        id={`analytics-panel-${tab}`}
        aria-labelledby={`analytics-tab-${tab}`}
        className="flex flex-col gap-6"
      >
        {panels[tab]}
      </section>
    </div>
  )
}