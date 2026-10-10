import type { PageResponse } from '@/types/pagination'
import type {
  RecurrenceFrequency,
  RecurringRunStatus,
  RecurringStatus,
  TransactionDirection,
  TransactionType,
} from '@/types/enums'

export interface RecurringAccountRef {
  id: number
  name: string
  currencyCode: string
}

export interface RecurringCategoryRef {
  id: number
  name: string
  type: 'INCOME' | 'EXPENSE'
  color: string | null
  icon: string | null
}

export interface RecurringTransaction {
  id: number
  account: RecurringAccountRef
  toAccount: RecurringAccountRef | null
  category: RecurringCategoryRef | null
  type: TransactionType
  direction: TransactionDirection
  amountMinor: number
  currencyCode: string
  baseAmountMinor: number
  description: string | null
  notes: string | null
  frequency: RecurrenceFrequency
  intervalCount: number
  dayOfMonth: number | null
  dayOfWeek: number | null
  skipWeekends: boolean
  startDate: string
  endDate: string | null
  targetOccurrenceDate: string | null
  nextRunDate: string | null
  lastRunDate: string | null
  status: RecurringStatus
  autoPost: boolean
  maxRetries: number
  retryCount: number
  lastError: string | null
  failureReason: string | null
  generatedCount: number
  createdAt: string
}

export type RecurringPage = PageResponse<RecurringTransaction>

export interface RecurringOccurrence {
  date: string
  estimatedAmountMinor: number
  willRun: boolean
}

export interface OccurrencePreview {
  occurrences: RecurringOccurrence[]
}

export interface CreateRecurringResponse {
  rule: RecurringTransaction
  occurrences: RecurringOccurrence[]
}

export interface RecurringRun {
  id: number
  scheduledFor: string
  executedAt: string
  status: RecurringRunStatus
  transactionId: number | null
  attempts: number
  errorMessage: string | null
}

export type RecurringRunPage = PageResponse<RecurringRun>

export interface RecurringFilter {
  status?: RecurringStatus
  type?: TransactionType
  accountId?: number
  categoryId?: number
  dueWithinDays?: number
  page?: number
  size?: number
}

export interface CreateRecurringInput {
  accountId: number
  toAccountId: number | null
  categoryId: number | null
  type: TransactionType
  amountMinor: number
  currencyCode: string
  description: string | null
  notes: string | null
  frequency: RecurrenceFrequency
  intervalCount: number
  dayOfMonth: number | null
  dayOfWeek: number | null
  skipWeekends: boolean
  startDate: string
  endDate: string | null
  autoPost: boolean
  maxRetries: number
  targetOccurrenceDate: string | null
}

export type UpdateRecurringInput = Partial<Omit<CreateRecurringInput, 'type' | 'currencyCode'>> & {
  type?: TransactionType
  currencyCode?: string
  version?: number
}