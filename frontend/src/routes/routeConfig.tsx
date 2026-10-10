import type { ComponentType } from 'react'
import type { RouteObject } from 'react-router-dom'
import { AuthLayout } from '@/layouts/AuthLayout'
import { AppShell } from '@/layouts/AppShell'
import { GuestOnlyRoute } from './GuestOnlyRoute'
import { ProtectedRoute } from './ProtectedRoute'
import { paths } from './paths'
import { NotFoundPage } from '@/pages/NotFoundPage'

const lazyPage = (loader: () => Promise<{ [key: string]: unknown }>, exportName: string) => ({
  lazy: async () => {
    const module = await loader()
    return { Component: module[exportName] as ComponentType }
  },
})

export const routeConfig: RouteObject[] = [
  {
    element: (
      <GuestOnlyRoute>
        <AuthLayout />
      </GuestOnlyRoute>
    ),
    children: [
      {
        path: paths.login,
        ...lazyPage(() => import('@/features/auth/pages/LoginPage'), 'LoginPage'),
      },
      {
        path: paths.register,
        ...lazyPage(() => import('@/features/auth/pages/RegisterPage'), 'RegisterPage'),
      },
      {
        path: paths.forgotPassword,
        ...lazyPage(() => import('@/features/auth/pages/ForgotPasswordPage'), 'ForgotPasswordPage'),
      },
      {
        path: paths.resetPassword,
        ...lazyPage(() => import('@/features/auth/pages/ResetPasswordPage'), 'ResetPasswordPage'),
      },
    ],
  },
  {
    element: (
      <ProtectedRoute>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        ...lazyPage(() => import('@/features/dashboard/pages/DashboardPage'), 'DashboardPage'),
      },
      {
        path: paths.dashboard,
        ...lazyPage(() => import('@/features/dashboard/pages/DashboardPage'), 'DashboardPage'),
      },
      {
        path: paths.transactions,
        ...lazyPage(
          () => import('@/features/transactions/pages/TransactionsPage'),
          'TransactionsPage',
        ),
      },
      {
        path: '/accounts',
        ...lazyPage(() => import('@/features/accounts/pages/AccountsPage'), 'AccountsPage'),
      },
      {
        path: '/accounts/:id',
        ...lazyPage(
          () => import('@/features/accounts/pages/AccountDetailPage'),
          'AccountDetailPage',
        ),
      },
      {
        path: '/categories',
        ...lazyPage(() => import('@/features/categories/pages/CategoriesPage'), 'CategoriesPage'),
      },
      {
        path: '/budgets',
        ...lazyPage(() => import('@/features/budgets/pages/BudgetsPage'), 'BudgetsPage'),
      },
      {
        path: '/budgets/:id',
        ...lazyPage(
          () => import('@/features/budgets/pages/BudgetDetailPage'),
          'BudgetDetailPage',
        ),
      },
      {
        path: '/goals',
        ...lazyPage(() => import('@/features/goals/pages/GoalsPage'), 'GoalsPage'),
      },
      {
        path: '/goals/:id',
        ...lazyPage(() => import('@/features/goals/pages/GoalDetailPage'), 'GoalDetailPage'),
      },
      {
        path: paths.recurring,
        ...lazyPage(() => import('@/features/recurring/pages/RecurringPage'), 'RecurringPage'),
      },
      {
        path: paths.import,
        ...lazyPage(() => import('@/features/imports/pages/ImportPage'), 'ImportPage'),
      },
      {
        path: paths.importHistory,
        ...lazyPage(
          () => import('@/features/imports/pages/ImportHistoryPage'),
          'ImportHistoryPage',
        ),
      },
      {
        path: '/import/:id',
        ...lazyPage(() => import('@/features/imports/pages/ImportPage'), 'ImportPage'),
      },
      { path: '/analytics', ...lazyPage(() => import('@/features/analytics/pages/AnalyticsPage'), 'AnalyticsPage') },
      {
        path: paths.notifications,
        ...lazyPage(
          () => import('@/features/notifications/pages/NotificationsPage'),
          'NotificationsPage',
        ),
      },
      {
        path: paths.notificationSettings,
        ...lazyPage(
          () => import('@/features/notifications/pages/NotificationPreferencesPage'),
          'NotificationPreferencesPage',
        ),
      },
      {
        path: '/settings/*',
        ...lazyPage(() => import('@/features/settings/pages/SettingsPage'), 'SettingsPage'),
      },
      { path: paths.notFound, element: <NotFoundPage /> },
    ],
  },
]
