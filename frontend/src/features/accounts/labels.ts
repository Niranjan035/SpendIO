import type { AccountStatus, AccountType } from '@/types/enums'

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CASH: 'Cash',
  BANK: 'Bank account',
  CREDIT_CARD: 'Credit card',
  WALLET: 'Wallet',
  INVESTMENT: 'Investment',
  LOAN: 'Loan',
}

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
}

export function accountTypeColor(type: AccountType): string {
  switch (type) {
    case 'CASH':
      return '#16a34a'
    case 'BANK':
      return '#2563eb'
    case 'CREDIT_CARD':
      return '#dc2626'
    case 'WALLET':
      return '#9333ea'
    case 'INVESTMENT':
      return '#0891b2'
    case 'LOAN':
      return '#ea580c'
  }
}
