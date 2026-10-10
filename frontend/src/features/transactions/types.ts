import type { Category } from '@/features/categories/types'
import type { PageResponse } from '@/types/pagination'
import type { TransactionDirection, TransactionStatus, TransactionType } from '@/types/enums'

export interface TransactionAccountRef {
  id: number
  name: string
  currencyCode: string
}

export interface TransactionCategoryRef {
  id: number
  name: string
  type: 'INCOME' | 'EXPENSE'
  color: string | null
  icon: string | null
}

export interface Transaction {
  id: number
  account: TransactionAccountRef
  category: TransactionCategoryRef | null
  type: TransactionType
  direction: TransactionDirection
  amountMinor: number
  currencyCode: string
  exchangeRate: number
  baseAmountMinor: number
  baseCurrencyCode: string
  occurredOn: string
  description: string | null
  merchant: string | null
  payee: string | null
  notes: string | null
  status: TransactionStatus
  transferGroupId: string | null
  reversalOfTransactionId: number | null
  reversedAt: string | null
  recurringTransactionId: number | null
  csvImportId: number | null
  createdAt: string
  updatedAt: string
  version: number
}

export type TransactionPage = PageResponse<Transaction>

export interface TransactionSummary {
  count: number
  incomeBaseMinor: number
  expenseBaseMinor: number
  netBaseMinor: number
  baseCurrencyCode: string
}

export interface AccountOption {
  id: number
  name: string
  currencyCode: string
  status: 'ACTIVE' | 'ARCHIVED'
}

export interface TransactionFilterOptions {
  accounts: AccountOption[]
  categories: Category[]
  currencies: string[]
  minDate: string | null
  maxDate: string | null
  typeCounts: Partial<Record<TransactionType, number>>
}

export interface DuplicateCheck {
  duplicate: boolean
  matchedTransactionId: number | null
  confidence: 'HIGH' | 'LOW' | 'NONE'
  reason: string | null
}

export interface BulkUpdateResult {
  updated: number
}

export interface TransactionFilter {
  q?: string
  accountIds?: number[]
  categoryIds?: number[]
  categoryType?: 'INCOME' | 'EXPENSE'
  types?: TransactionType[]
  directions?: TransactionDirection[]
  statuses?: TransactionStatus[]
  from?: string
  to?: string
  minAmountMinor?: number
  maxAmountMinor?: number
  currencyCode?: string
  baseCurrencyOnly?: boolean
  recurringOnly?: boolean
  importedOnly?: boolean
  hasNotes?: boolean
  hasReversal?: boolean
  sort?: string
  page?: number
  size?: number
}

export interface CreateTransactionInput {
  accountId: number
  categoryId: number | null
  type: TransactionType
  direction: TransactionDirection | null
  amountMinor: number
  currencyCode: string
  exchangeRate: number | null
  occurredOn: string
  description: string | null
  merchant: string | null
  payee: string | null
  notes: string | null
  status: TransactionStatus | null
  dedupeKey: string | null
}

export type UpdateTransactionInput = {
  accountId: number
  categoryId: number | null
  amountMinor: number
  currencyCode: string
  occurredOn: string
  description: string
  notes: string
  version: number
}

export interface ReverseTransactionInput {
  reason: string
  occurredOn: string
}

export interface CreateTransferInput {
  fromAccountId: number
  toAccountId: number
  amountMinor: number
  currencyCode: string
  toAmountMinor: number | null
  occurredOn: string
  description: string | null
  notes: string | null
}

export interface Transfer {
  transferGroupId: string
  fromTransaction: Transaction
  toTransaction: Transaction
}
