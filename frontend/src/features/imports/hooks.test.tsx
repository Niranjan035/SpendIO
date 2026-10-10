import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { importsApi } from './api'
import {
  importsKeys,
  useApplyRowDecisions,
  useExecuteImport,
  useImport,
  useImportHistory,
  useUploadImport,
} from './hooks'
import type { ImportReport, ImportSummary } from './types'

vi.mock('./api', () => ({
  importsApi: {
    list: vi.fn(),
    upload: vi.fn(),
    detail: vi.fn(),
    remove: vi.fn(),
    columns: vi.fn(),
    saveMapping: vi.fn(),
    validate: vi.fn(),
    preview: vi.fn(),
    duplicates: vi.fn(),
    updateRow: vi.fn(),
    applyDecisions: vi.fn(),
    execute: vi.fn(),
    report: vi.fn(),
    errorsCsv: vi.fn(),
    undo: vi.fn(),
  },
}))

const summary: ImportSummary = {
  id: 7,
  status: 'READY',
  originalFilename: 'bank-march.csv',
  fileSizeBytes: 2048,
  detectedDelimiter: ',',
  detectedEncoding: 'UTF-8',
  headerRowNumber: 1,
  columns: ['Date', 'Amount'],
  mapping: { Date: { field: 'date' }, Amount: { field: 'amount' } },
  totals: { total: 2, valid: 2, invalid: 0, duplicate: 0, created: 0, skipped: 0, failed: 0 },
  errorSummary: null,
  startedAt: null,
  completedAt: null,
  createdAt: '2026-03-09T10:00:00Z',
}

const report: ImportReport = {
  import_: { ...summary, status: 'COMPLETED' },
  createdTransactions: [],
  failures: [],
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('queries', () => {
  it('loads a job into the detail cache', async () => {
    vi.mocked(importsApi.detail).mockResolvedValue(summary)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useImport(7), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.status).toBe('READY')
    expect(queryClient.getQueryData(importsKeys.detail(7))).toMatchObject({ id: 7 })
  })

  it('passes the history filter through to the API', async () => {
    vi.mocked(importsApi.list).mockResolvedValue({
      content: [summary],
      page: 1,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: false,
      last: true,
    })
    const { wrapper } = createWrapper()

    const { result } = renderHook(
      () => useImportHistory({ status: 'COMPLETED', page: 1, size: 20 }),
      { wrapper },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(importsApi.list).toHaveBeenCalledWith({ status: 'COMPLETED', page: 1, size: 20 })
    expect(result.current.data?.content).toHaveLength(1)
  })
})

describe('mutations', () => {
  it('stores the uploaded job and invalidates the history', async () => {
    vi.mocked(importsApi.upload).mockResolvedValue({ ...summary, status: 'MAPPING_REQUIRED' })
    const invalidate = vi
      .spyOn(QueryClient.prototype, 'invalidateQueries')
      .mockResolvedValue(undefined as never)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useUploadImport(), { wrapper })
    const file = new File(['a,b'], 'bank.csv', { type: 'text/csv' })

    result.current.mutate({ file })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(importsKeys.detail(7))).toMatchObject({
      status: 'MAPPING_REQUIRED',
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: importsKeys.all })
  })

  it('seeds both the report and the job cache after execute', async () => {
    vi.mocked(importsApi.execute).mockResolvedValue(report)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useExecuteImport(), { wrapper })

    result.current.mutate({ id: 7, input: { allowPartialImport: true } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(importsApi.execute).toHaveBeenCalledWith(7, { allowPartialImport: true })
    expect(queryClient.getQueryData(importsKeys.report(7))).toMatchObject({ import_: report.import_ })
    expect(queryClient.getQueryData(importsKeys.detail(7))).toMatchObject({ status: 'COMPLETED' })
  })

  it('invalidates everything under imports after decisions', async () => {
    vi.mocked(importsApi.applyDecisions).mockResolvedValue(undefined)
    const invalidate = vi
      .spyOn(QueryClient.prototype, 'invalidateQueries')
      .mockResolvedValue(undefined as never)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useApplyRowDecisions(), { wrapper })

    result.current.mutate({ id: 7, input: { rowIds: [11, 12], decision: 'CONFIRM' } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(importsApi.applyDecisions).toHaveBeenCalledWith(7, {
      rowIds: [11, 12],
      decision: 'CONFIRM',
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: importsKeys.all })
  })
})
