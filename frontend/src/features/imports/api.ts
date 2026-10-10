import { buildUrl, http, type QueryValue } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import { toApiError } from '@/services/http/ApiError'
import { getAccessToken } from '@/services/http/tokenManager'
import type {
  ColumnMappingResponse,
  DuplicateGroup,
  ExecuteImportInput,
  ImportFilter,
  ImportHistoryPage,
  ImportPreviewFilter,
  ImportPreviewPage,
  ImportReport,
  ImportRowPreview,
  ImportSummary,
  RowDecisionInput,
  SaveMappingInput,
  UpdateRowInput,
  UploadImportInput,
} from './types'

/** Serialises the history filter into the E.12 query contract. */
export function buildHistoryQuery(filter: ImportFilter): Record<string, QueryValue> {
  return {
    status: filter.status,
    page: filter.page ?? 0,
    size: filter.size ?? 50,
  }
}

/** Serialises the preview filter into the E.12 query contract. */
export function buildPreviewQuery(filter: ImportPreviewFilter): Record<string, QueryValue> {
  return {
    status: filter.status,
    page: filter.page ?? 0,
    size: filter.size ?? 50,
  }
}

/** Uploads the CSV as multipart, then returns the parsed job summary (E.12 POST /imports). */
export function uploadFormData(input: UploadImportInput): FormData {
  const data = new FormData()
  data.append('file', input.file, input.file.name)
  if (input.defaultCurrencyCode) data.append('defaultCurrencyCode', input.defaultCurrencyCode)
  if (input.defaultDateFormat) data.append('defaultDateFormat', input.defaultDateFormat)
  if (input.delimiterOverride) data.append('delimiterOverride', input.delimiterOverride)
  if (input.encodingOverride) data.append('encodingOverride', input.encodingOverride)
  if (input.headerRowNumber !== undefined) data.append('headerRowNumber', String(input.headerRowNumber))
  if (input.targetAccountId !== undefined) data.append('targetAccountId', String(input.targetAccountId))
  return data
}

export const importsApi = {
  list(filter: ImportFilter = {}): Promise<ImportHistoryPage> {
    return http.get<ImportHistoryPage>(endpoints.imports.list, { query: buildHistoryQuery(filter) })
  },

  upload(input: UploadImportInput): Promise<ImportSummary> {
    return http.post<ImportSummary>(endpoints.imports.upload, undefined, {
      formData: uploadFormData(input),
    })
  },

  detail(id: number): Promise<ImportSummary> {
    return http.get<ImportSummary>(endpoints.imports.detail(String(id)))
  },

  remove(id: number): Promise<void> {
    return http.delete<void>(endpoints.imports.remove(String(id)))
  },

  columns(id: number): Promise<ColumnMappingResponse> {
    return http.get<ColumnMappingResponse>(endpoints.imports.columns(String(id)))
  },

  saveMapping(id: number, input: SaveMappingInput): Promise<ImportSummary> {
    return http.put<ImportSummary>(endpoints.imports.mapping(String(id)), input)
  },

  validate(id: number): Promise<ImportSummary> {
    return http.post<ImportSummary>(endpoints.imports.validate(String(id)))
  },

  preview(id: number, filter: ImportPreviewFilter = {}): Promise<ImportPreviewPage> {
    return http.get<ImportPreviewPage>(endpoints.imports.preview(String(id)), {
      query: buildPreviewQuery(filter),
    })
  },

  duplicates(id: number): Promise<DuplicateGroup[]> {
    return http.get<DuplicateGroup[]>(endpoints.imports.duplicates(String(id)))
  },

  updateRow(id: number, rowId: number, input: UpdateRowInput): Promise<ImportRowPreview> {
    return http.patch<ImportRowPreview>(endpoints.imports.row(String(id), String(rowId)), input)
  },

  applyDecisions(id: number, input: RowDecisionInput): Promise<void> {
    return http.post<void>(endpoints.imports.decisions(String(id)), input)
  },

  execute(id: number, input: ExecuteImportInput = {}): Promise<ImportReport> {
    return http.post<ImportReport>(endpoints.imports.execute(String(id)), input)
  },

  report(id: number): Promise<ImportReport> {
    return http.get<ImportReport>(endpoints.imports.report(String(id)))
  },

  /**
   * Downloads the row-level errors CSV. The shared client only speaks JSON, so the
   * download goes through `fetch` and returns the file to save (mirrors exportCsv).
   */
  async errorsCsv(id: number): Promise<{ blob: Blob; filename: string }> {
    const token = getAccessToken()
    const response = await fetch(buildUrl(endpoints.imports.errorsCsv(String(id))), {
      method: 'GET',
      headers: {
        Accept: 'text/csv',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    })
    if (!response.ok) throw await toApiError(response)
    const disposition = response.headers.get('Content-Disposition') ?? ''
    const match = /filename="([^"]+)"/.exec(disposition)
    return {
      blob: await response.blob(),
      filename: match?.[1] ?? `import-${id}-errors.csv`,
    }
  },

  undo(id: number): Promise<{ undone: number }> {
    return http.post<{ undone: number }>(endpoints.imports.undo(String(id)))
  },
}
