export type CurrencyCode = string

export type AccountType = 'CASH' | 'BANK' | 'CREDIT_CARD' | 'WALLET' | 'INVESTMENT' | 'LOAN'

export type AccountStatus = 'ACTIVE' | 'ARCHIVED'

export type TransactionType = 'INCOME' | 'EXPENSE' | 'ADJUSTMENT' | 'TRANSFER'

export type TransactionDirection = 'INFLOW' | 'OUTFLOW'

export type TransactionStatus = 'POSTED' | 'PENDING' | 'VOID'

export type CategoryType = 'INCOME' | 'EXPENSE'

export type BudgetPeriod = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY' | 'CUSTOM'

export type BudgetStatus = 'ACTIVE' | 'ARCHIVED'

export type PaceStatus = 'UNDER_PACE' | 'ON_PACE' | 'OVER_PACE' | 'EXCEEDED'

export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'PAUSED' | 'CANCELLED'

export type GoalPriority = 0 | 1 | 2 | 3 | 4 | 5

export type GoalContributionMethod = 'MANUAL' | 'TRANSFER' | 'ADJUSTMENT'

export type ImportStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'PARSED'
  | 'MAPPING_REQUIRED'
  | 'VALIDATING'
  | 'READY'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'FAILED'

export type ImportRowStatus =
  | 'PENDING'
  | 'VALID'
  | 'INVALID'
  | 'DUPLICATE'
  | 'CONFIRMED'
  | 'SKIPPED'
  | 'CREATED'
  | 'FAILED'
  | 'UNDONE'

export type NotificationChannel = 'IN_APP' | 'EMAIL'

export type Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'

export type SortDirection = 'asc' | 'desc'

export type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY' | 'CUSTOM'

export type RecurringStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export type RecurringRunStatus = 'SUCCESS' | 'SKIPPED' | 'FAILED' | 'SKIPPED_DUPLICATE' | 'SKIPPED_PAUSED'

export const ACCOUNT_TYPES: readonly AccountType[] = [
  'CASH',
  'BANK',
  'CREDIT_CARD',
  'WALLET',
  'INVESTMENT',
  'LOAN',
]

export const BUDGET_PERIODS: readonly BudgetPeriod[] = [
  'WEEKLY',
  'MONTHLY',
  'QUARTERLY',
  'YEARLY',
  'CUSTOM',
]

export const BUDGET_STATUSES: readonly BudgetStatus[] = ['ACTIVE', 'ARCHIVED']

export const GOAL_STATUSES: readonly GoalStatus[] = ['ACTIVE', 'COMPLETED', 'PAUSED', 'CANCELLED']

export const GOAL_PRIORITIES: readonly GoalPriority[] = [0, 1, 2, 3, 4, 5]

export const GOAL_CONTRIBUTION_METHODS: readonly GoalContributionMethod[] = [
  'MANUAL',
  'TRANSFER',
  'ADJUSTMENT',
]

export const TRANSACTION_TYPES: readonly TransactionType[] = [
  'INCOME',
  'EXPENSE',
  'ADJUSTMENT',
  'TRANSFER',
]

export const TRANSACTION_STATUSES: readonly TransactionStatus[] = ['POSTED', 'PENDING', 'VOID']

export const RECURRENCE_FREQUENCIES: readonly RecurrenceFrequency[] = [
  'DAILY',
  'WEEKLY',
  'MONTHLY',
  'YEARLY',
  'CUSTOM',
]

export const RECURRING_STATUSES: readonly RecurringStatus[] = [
  'ACTIVE',
  'PAUSED',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
]

export const RECURRING_RUN_STATUSES: readonly RecurringRunStatus[] = [
  'SUCCESS',
  'SKIPPED',
  'FAILED',
  'SKIPPED_DUPLICATE',
  'SKIPPED_PAUSED',
]

export const IMPORT_STATUSES: readonly ImportStatus[] = [
  'UPLOADED',
  'PARSING',
  'PARSED',
  'MAPPING_REQUIRED',
  'VALIDATING',
  'READY',
  'PROCESSING',
  'COMPLETED',
  'COMPLETED_WITH_ERRORS',
  'FAILED',
]

export const IMPORT_ROW_STATUSES: readonly ImportRowStatus[] = [
  'PENDING',
  'VALID',
  'INVALID',
  'DUPLICATE',
  'CONFIRMED',
  'SKIPPED',
  'CREATED',
  'FAILED',
  'UNDONE',
]
