import { describe, expect, it } from 'vitest'
import { endpoints } from './endpoints'

describe('endpoints', () => {
  it('matches the backend Phase 1 contract', () => {
    expect(endpoints.profile.me).toBe('/users/me')
    expect(endpoints.profile.preferences).toBe('/users/me/preferences')
    expect(endpoints.profile.baseCurrency).toBe('/users/me/base-currency')
    expect(endpoints.reference.currencies).toBe('/currencies')
  })

  it('matches the backend Phase 2 auth contract', () => {
    expect(endpoints.auth.register).toBe('/auth/register')
    expect(endpoints.auth.login).toBe('/auth/login')
    expect(endpoints.auth.refresh).toBe('/auth/refresh')
    expect(endpoints.auth.logout).toBe('/auth/logout')
    expect(endpoints.auth.logoutAll).toBe('/auth/logout-all')
    expect(endpoints.auth.forgotPassword).toBe('/auth/forgot-password')
    expect(endpoints.auth.resetPassword).toBe('/auth/reset-password')
    expect(endpoints.auth.verifyEmail).toBe('/auth/verify-email')
    expect(endpoints.auth.resendVerification).toBe('/auth/resend-verification')
    expect(endpoints.auth.changePassword).toBe('/auth/change-password')
    expect(endpoints.auth.sessions).toBe('/auth/sessions')
  })

  it('matches the backend Phase 3 accounts contract', () => {
    expect(endpoints.accounts.list).toBe('/accounts')
    expect(endpoints.accounts.create).toBe('/accounts')
    expect(endpoints.accounts.summary).toBe('/accounts/summary')
    expect(endpoints.accounts.detail('7')).toBe('/accounts/7')
    expect(endpoints.accounts.update('7')).toBe('/accounts/7')
    expect(endpoints.accounts.remove('7')).toBe('/accounts/7')
    expect(endpoints.accounts.archive('7')).toBe('/accounts/7/archive')
    expect(endpoints.accounts.restore('7')).toBe('/accounts/7/restore')
    expect(endpoints.accounts.setPrimary('7')).toBe('/accounts/7/set-primary')
    expect(endpoints.accounts.balance('7')).toBe('/accounts/7/balance')
    expect(endpoints.accounts.balances('7')).toBe('/accounts/7/balances')
    expect(endpoints.accounts.adjust('7')).toBe('/accounts/7/adjust')
  })

  it('matches the backend Phase 6 transactions contract', () => {
    expect(endpoints.transactions.list).toBe('/transactions')
    expect(endpoints.transactions.summary).toBe('/transactions/summary')
    expect(endpoints.transactions.options).toBe('/transactions/filters/options')
    expect(endpoints.transactions.export).toBe('/transactions/export')
    expect(endpoints.transactions.checkDuplicate).toBe('/transactions/check-duplicate')
    expect(endpoints.transactions.bulk).toBe('/transactions/bulk')
    expect(endpoints.transactions.detail('7')).toBe('/transactions/7')
    expect(endpoints.transactions.update('7')).toBe('/transactions/7')
    expect(endpoints.transactions.remove('7')).toBe('/transactions/7')
    expect(endpoints.transactions.reverse('7')).toBe('/transactions/7/reverse')
  })

  it('matches the backend Phase 7 transfers contract', () => {
    expect(endpoints.transfers.list).toBe('/transfers')
    expect(endpoints.transfers.create).toBe('/transfers')
    expect(endpoints.transfers.detail('g-1')).toBe('/transfers/g-1')
    expect(endpoints.transfers.reverse('g-1')).toBe('/transfers/g-1/reverse')
  })

  it('matches the backend Phase 13 analytics & dashboard contract', () => {
    expect(endpoints.analytics.cashFlow).toBe('/analytics/cash-flow')
    expect(endpoints.analytics.spendingTrend).toBe('/analytics/spending-trend')
    expect(endpoints.analytics.incomeVsExpense).toBe('/analytics/income-vs-expense')
    expect(endpoints.analytics.categoryBreakdown).toBe('/analytics/category-breakdown')
    expect(endpoints.analytics.topSpending).toBe('/analytics/top-spending')
    expect(endpoints.analytics.monthlyComparison).toBe('/analytics/monthly-comparison')
    expect(endpoints.analytics.budgetUtilization).toBe('/analytics/budget-utilization')
    expect(endpoints.analytics.goalProgress).toBe('/analytics/goal-progress')
    expect(endpoints.analytics.netWorth).toBe('/analytics/net-worth')
    expect(endpoints.analytics.accounts).toBe('/analytics/accounts')
    expect(endpoints.dashboard.summary).toBe('/dashboard/summary')
    expect(endpoints.dashboard.recentTransactions).toBe('/dashboard/recent-transactions')
    expect(endpoints.dashboard.upcomingRecurring).toBe('/dashboard/upcoming-recurring')
  })

  it('matches the backend Phase 14 settings & audit contract', () => {
    expect(endpoints.profile.export).toBe('/users/me/export')
    expect(endpoints.profile.delete).toBe('/users/me')
    expect(endpoints.audit.list).toBe('/audit-logs')
  })

  it('encodes path parameters', () => {
    expect(endpoints.accounts.detail('a/b')).toBe('/accounts/a%2Fb')
    expect(endpoints.accounts.adjust('r 1')).toBe('/accounts/r%201/adjust')
    expect(endpoints.auth.revokeSession('r 1')).toBe('/auth/sessions/r%201')
  })
})
