/** Route path constants — the single source of truth for navigation. */
export const paths = {
  login: '/login',
  register: '/register',
  forgotPassword: '/forgot-password',
  resetPassword: '/reset-password',
  dashboard: '/dashboard',
  transactions: '/transactions',
  transaction: (id: string) => `/transactions/${id}`,
  accounts: '/accounts',
  account: (id: string) => `/accounts/${id}`,
  categories: '/categories',
  budgets: '/budgets',
  budget: (id: string) => `/budgets/${id}`,
  goals: '/goals',
  goal: (id: string) => `/goals/${id}`,
  recurring: '/recurring',
  import: '/import',
  importHistory: '/import/history',
  analytics: '/analytics',
  notifications: '/notifications',
  notificationSettings: '/notifications/preferences',
  settings: '/settings',
  profileSettings: '/settings/profile',
  securitySettings: '/settings/security',
  preferencesSettings: '/settings/preferences',
  dataSettings: '/settings/data',
  auditLog: '/settings/audit',
  notFound: '*',
} as const

/** Routes only an anonymous visitor may see. */
export const GUEST_ONLY_PATHS: readonly string[] = [
  paths.login,
  paths.register,
  paths.forgotPassword,
  paths.resetPassword,
]
