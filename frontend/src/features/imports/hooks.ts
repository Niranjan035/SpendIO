import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { importsApi } from './api'
import { importPollInterval } from './polling'
import type {
  ExecuteImportInput,
  ImportFilter,
  ImportHistoryPage,
  ImportPreviewFilter,
  ImportPreviewPage,
  ImportReport,
  ImportSummary,
  RowDecisionInput,
  SaveMappingInput,
  UpdateRowInput,
  UploadImportInput,
} from './types'

export const importsKeys = {
  all: ['imports'] as const,
  list: (filter: ImportFilter) => ['imports', 'list', filter] as const,
  detail: (id: number) => ['imports', 'detail', id] as const,
  columns: (id: number) => ['imports', 'columns', id] as const,
  preview: (id: number, filter: ImportPreviewFilter) => ['imports', 'preview', id, filter] as const,
  duplicates: (id: number) => ['imports', 'duplicates', id] as const,
  report: (id: number) => ['imports', 'report', id] as const,
}

function invalidateImports(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: importsKeys.all })
}

/**
 * Refetches while the job is moving through the pipeline on its own
 * (upload/parse/validate/process) and stops once it settles (polling.ts).
 */
export function useImport(id: number) {
  return useQuery<ImportSummary>({
    queryKey: importsKeys.detail(id),
    queryFn: () => importsApi.detail(id),
    enabled: id > 0,
    refetchInterval: (query) => importPollInterval(query.state.data?.status),
  })
}

export function useImportHistory(filter: ImportFilter) {
  return useQuery<ImportHistoryPage>({
    queryKey: importsKeys.list(filter),
    queryFn: () => importsApi.list(filter),
  })
}

export function useImportColumns(id: number) {
  return useQuery({
    queryKey: importsKeys.columns(id),
    queryFn: () => importsApi.columns(id),
    enabled: id > 0,
  })
}

export function useImportPreview(id: number, filter: ImportPreviewFilter) {
  return useQuery<ImportPreviewPage>({
    queryKey: importsKeys.preview(id, filter),
    queryFn: () => importsApi.preview(id, filter),
    enabled: id > 0,
  })
}

export function useImportDuplicates(id: number) {
  return useQuery({
    queryKey: importsKeys.duplicates(id),
    queryFn: () => importsApi.duplicates(id),
    enabled: id > 0,
  })
}

export function useImportReport(id: number) {
  return useQuery<ImportReport>({
    queryKey: importsKeys.report(id),
    queryFn: () => importsApi.report(id),
    enabled: id > 0,
  })
}

export function useUploadImport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: UploadImportInput) => importsApi.upload(input),
    onSuccess: (job) => {
      invalidateImports(client)
      client.setQueryData<ImportSummary>(importsKeys.detail(job.id), job)
    },
  })
}

export function useSaveImportMapping() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: SaveMappingInput }) =>
      importsApi.saveMapping(id, input),
    onSuccess: (job) => {
      invalidateImports(client)
      client.setQueryData<ImportSummary>(importsKeys.detail(job.id), job)
    },
  })
}

export function useValidateImport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => importsApi.validate(id),
    onSuccess: (job) => {
      invalidateImports(client)
      client.setQueryData<ImportSummary>(importsKeys.detail(job.id), job)
    },
  })
}

export function useUpdateImportRow() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, rowId, input }: { id: number; rowId: number; input: UpdateRowInput }) =>
      importsApi.updateRow(id, rowId, input),
    onSuccess: () => {
      invalidateImports(client)
    },
  })
}

export function useApplyRowDecisions() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: RowDecisionInput }) =>
      importsApi.applyDecisions(id, input),
    onSuccess: () => {
      invalidateImports(client)
    },
  })
}

export function useExecuteImport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: ExecuteImportInput }) =>
      importsApi.execute(id, input),
    onSuccess: (report, variables) => {
      invalidateImports(client)
      client.setQueryData<ImportReport>(importsKeys.report(variables.id), report)
      client.setQueryData<ImportSummary>(importsKeys.detail(variables.id), report.import_)
    },
  })
}

export function useUndoImport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => importsApi.undo(id),
    onSuccess: (_result, id) => {
      invalidateImports(client)
      void client.invalidateQueries({ queryKey: importsKeys.report(id) })
    },
  })
}

export function useDeleteImport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => importsApi.remove(id),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: importsKeys.detail(id) })
      invalidateImports(client)
    },
  })
}
