import type { PageResponse } from '@/types/pagination'
import type { ImportRowStatus, ImportStatus } from '@/types/enums'

export interface ImportTotals {
  total: number
  valid: number
  invalid: number
  duplicate: number
  created: number
  skipped: number
  failed: number
}

export interface ImportSummary {
  id: number
  status: ImportStatus
  originalFilename: string
  fileSizeBytes: number
  detectedDelimiter: string | null
  detectedEncoding: string | null
  headerRowNumber: number | null
  columns: string[]
  mapping: Record<string, Record<string, string>>
  totals: ImportTotals
  errorSummary: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

export type ImportHistoryPage = PageResponse<ImportSummary>

export interface ImportRowPreview {
  id: number
  rowNumber: number
  status: ImportRowStatus
  raw: Record<string, unknown>
  parsed: Record<string, unknown>
  occurredOn: string | null
  amountMinor: number | null
  currencyCode: string | null
  description: string | null
  type: string | null
  accountId: number | null
  categoryId: number | null
  matchedAccountId: number | null
  matchedCategoryId: number | null
  matchedBy: string | null
  confidence: number | null
  errors: string[]
  duplicateOfRowId: number | null
  duplicateOfTransactionId: number | null
  createdTransactionId: number | null
}

export type ImportPreviewPage = PageResponse<ImportRowPreview>

export interface ColumnInfo {
  index: number
  headerName: string
  sampleValues: string[]
  suggestedField: string | null
  confidence: number
}

export interface FieldPointer {
  field: string
  column: string
  formatHint: string | null
}

export interface ColumnMappingResponse {
  columns: ColumnInfo[]
  mapping: FieldPointer[]
  unmappedRequired: string[]
}

export interface DuplicateRow {
  id: number
  rowNumber: number
  status: string
  occurredOn: string | null
  amountMinor: number | null
  currencyCode: string | null
  description: string | null
}

export interface DuplicateGroup {
  dedupeHash: string
  rows: DuplicateRow[]
  existingTransactionIds: number[]
}

export interface ImportReportTransaction {
  id: number
  occurredOn: string
  amountMinor: number
  currencyCode: string
  description: string | null
}

export interface ImportReportFailure {
  rowNumber: number
  reason: string
  raw: Record<string, unknown>
}

export interface ImportReport {
  import_: ImportSummary
  createdTransactions: ImportReportTransaction[]
  failures: ImportReportFailure[]
}

export interface ImportFilter {
  status?: ImportStatus
  page?: number
  size?: number
}

export interface ImportPreviewFilter {
  status?: ImportRowStatus
  page?: number
  size?: number
}

export interface UploadImportInput {
  file: File
  defaultCurrencyCode?: string
  defaultDateFormat?: string
  delimiterOverride?: string
  encodingOverride?: string
  headerRowNumber?: number
  targetAccountId?: number
}

export interface ImportFieldMapping {
  field: string
  formatHint?: string
}

export interface SaveMappingInput {
  columnMappings: Record<string, ImportFieldMapping>
  dateColumn?: string
  amountColumn?: string
  descriptionColumn?: string
  debitColumn?: string
  creditColumn?: string
  categoryColumn?: string
  accountColumn?: string
  currencyColumn?: string
  typeColumn?: string
  dateFormat?: string
  amountFormat?: string
  options?: {
    defaultCurrencyCode?: string
    defaultDateFormat?: string
    targetAccountId?: number
    createMissingCategories?: boolean
    skipDuplicates?: boolean
    allowPartialImport?: boolean
    defaultType?: string
  }
}

export interface RowDecisionInput {
  rowIds: number[]
  decision: 'CONFIRM' | 'SKIP' | 'UNCONFIRM'
}

export interface ExecuteImportInput {
  rowIds?: number[]
  allowPartialImport?: boolean
  dryRun?: boolean
}

export interface UpdateRowInput {
  occurredOn?: string
  amountMinor: number
  currencyCode?: string
  description?: string
  accountId?: number
  categoryId?: number
  type?: string
  notes?: string
}
