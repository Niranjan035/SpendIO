import type { AccountStatus, AccountType } from '@/types/enums'
import type { PageResponse } from '@/types/pagination'

export interface Account {
  id: number
  name: string
  type: AccountType
  currencyCode: string
  openingBalanceMinor: number
  openingBalanceDate: string | null
  institution: string | null
  last4: string | null
  creditLimitMinor: number | null
  billingDay: number | null
  dueDay: number | null
  icon: string | null
  color: string | null
  notes: string | null
  primary: boolean
  status: AccountStatus
  transactionCount: number
  createdAt: string
  balanceMinor: number
  balanceBaseMinor: number
  baseCurrencyCode: string
  availableCreditMinor: number | null
}

export interface AccountBalance {
  accountId: number
  currencyCode: string
  balanceMinor: number
  baseCurrencyCode: string
  balanceBaseMinor: number
  asOf: string
  availableCreditMinor: number | null
}

export interface AccountTotals {
  totalBaseMinor: number
  baseCurrencyCode: string
  byType: Partial<Record<AccountType, number>>
  byCurrency: Record<string, number>
}

export interface DailyBalancePoint {
  date: string
  balanceMinor: number
  balanceBaseMinor: number
}

export type AccountPage = PageResponse<Account>

export interface AccountFilter {
  type?: AccountType
  status?: AccountStatus
  currencyCode?: string
  q?: string
  includeArchived?: boolean
  hasTransactions?: boolean
  sort?: string
  page?: number
  size?: number
}

export interface CreateAccountInput {
  name: string
  type: AccountType
  currencyCode: string
  openingBalanceMinor: number
  openingBalanceDate: string | null
  institution: string | null
  last4: string | null
  creditLimitMinor: number | null
  billingDay: number | null
  dueDay: number | null
  icon: string | null
  color: string | null
  notes: string | null
  primary: boolean
  openingTransactionDate: string | null
  createOpeningBalanceTransaction: boolean
}

export type UpdateAccountInput = Partial<{
  name: string
  type: AccountType
  currencyCode: string
  institution: string | null
  last4: string | null
  creditLimitMinor: number | null
  billingDay: number | null
  dueDay: number | null
  icon: string | null
  color: string | null
  notes: string | null
}>

export interface AdjustBalanceInput {
  newBalanceMinor: number
  occurredOn: string
  reason: string
}
