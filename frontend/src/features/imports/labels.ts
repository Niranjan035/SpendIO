import type { BadgeTone } from '@/components/ui/Badge'
import type { ImportRowStatus, ImportStatus } from '@/types/enums'

export const IMPORT_STATUS_LABELS: Record<ImportStatus, string> = {
  UPLOADED: 'Uploaded',
  PARSING: 'Parsing',
  PARSED: 'Parsed',
  MAPPING_REQUIRED: 'Map columns',
  VALIDATING: 'Validating',
  READY: 'Ready to import',
  PROCESSING: 'Importing',
  COMPLETED: 'Imported',
  COMPLETED_WITH_ERRORS: 'Imported with errors',
  FAILED: 'Failed',
}

export const IMPORT_ROW_STATUS_LABELS: Record<ImportRowStatus, string> = {
  PENDING: 'Pending',
  VALID: 'Valid',
  INVALID: 'Invalid',
  DUPLICATE: 'Duplicate',
  CONFIRMED: 'Confirmed',
  SKIPPED: 'Skipped',
  CREATED: 'Created',
  FAILED: 'Failed',
  UNDONE: 'Undone',
}

/** CSV columns the backend can be pointed at, in the order the mapper shows them. */
export const IMPORT_MAPPING_FIELDS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'date', label: 'Date' },
  { value: 'amount', label: 'Amount' },
  { value: 'debit', label: 'Debit' },
  { value: 'credit', label: 'Credit' },
  { value: 'description', label: 'Description' },
  { value: 'category', label: 'Category' },
  { value: 'account', label: 'Account' },
  { value: 'currency', label: 'Currency' },
  { value: 'type', label: 'Type' },
]

export const IMPORT_FIELD_LABELS: Record<string, string> = Object.fromEntries(
  IMPORT_MAPPING_FIELDS.map((field) => [field.value, field.label]),
)

export function importStatusTone(status: ImportStatus): BadgeTone {
  switch (status) {
    case 'UPLOADED':
    case 'PARSING':
    case 'PARSED':
      return 'info'
    case 'MAPPING_REQUIRED':
      return 'warning'
    case 'VALIDATING':
    case 'PROCESSING':
      return 'info'
    case 'READY':
      return 'primary'
    case 'COMPLETED':
      return 'success'
    case 'COMPLETED_WITH_ERRORS':
      return 'warning'
    case 'FAILED':
      return 'danger'
  }
}

export function importRowStatusTone(status: ImportRowStatus): BadgeTone {
  switch (status) {
    case 'PENDING':
    case 'SKIPPED':
      return 'neutral'
    case 'VALID':
    case 'CONFIRMED':
      return 'success'
    case 'INVALID':
    case 'FAILED':
      return 'danger'
    case 'DUPLICATE':
      return 'warning'
    case 'CREATED':
      return 'info'
    case 'UNDONE':
      return 'neutral'
  }
}

/** The import state machine has at most one visible step per status (ARCHITECTURE.md D.12). */
export function importStepForStatus(status: ImportStatus): 'upload' | 'map' | 'review' | 'done' {
  switch (status) {
    case 'UPLOADED':
    case 'PARSING':
      return 'upload'
    case 'PARSED':
    case 'MAPPING_REQUIRED':
      return 'map'
    case 'VALIDATING':
    case 'READY':
    case 'PROCESSING':
      return 'review'
    case 'COMPLETED':
    case 'COMPLETED_WITH_ERRORS':
    case 'FAILED':
      return 'done'
  }
}
