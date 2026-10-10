import type { BadgeTone } from '@/components/ui/Badge'
import type { TransactionDirection, TransactionStatus, TransactionType } from '@/types/enums'

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  INCOME: 'Income',
  EXPENSE: 'Expense',
  ADJUSTMENT: 'Adjustment',
  TRANSFER: 'Transfer',
}

export const TRANSACTION_STATUS_LABELS: Record<TransactionStatus, string> = {
  POSTED: 'Posted',
  PENDING: 'Pending',
  VOID: 'Void',
}

export const TRANSACTION_DIRECTION_LABELS: Record<TransactionDirection, string> = {
  INFLOW: 'Inflow',
  OUTFLOW: 'Outflow',
}

export const TRANSACTION_SORT_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: '', label: 'Newest first' },
  { value: 'occurredOn,asc', label: 'Oldest first' },
  { value: 'amount,desc', label: 'Largest amount' },
  { value: 'amount,asc', label: 'Smallest amount' },
  { value: 'description,asc', label: 'Description A–Z' },
  { value: 'account,asc', label: 'Account A–Z' },
  { value: 'category,asc', label: 'Category A–Z' },
  { value: 'createdAt,desc', label: 'Recently added' },
]

export function transactionTypeTone(type: TransactionType): BadgeTone {
  switch (type) {
    case 'INCOME':
      return 'success'
    case 'EXPENSE':
      return 'danger'
    case 'ADJUSTMENT':
      return 'info'
    case 'TRANSFER':
      return 'primary'
  }
}

export function transactionStatusTone(status: TransactionStatus): BadgeTone {
  switch (status) {
    case 'POSTED':
      return 'neutral'
    case 'PENDING':
      return 'warning'
    case 'VOID':
      return 'danger'
  }
}
