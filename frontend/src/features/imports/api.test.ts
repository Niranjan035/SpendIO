import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildHistoryQuery,
  buildPreviewQuery,
  importsApi,
  uploadFormData,
} from './api'
import type { SaveMappingInput, UploadImportInput } from './types'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function call(
  fetchMock: { mock: { calls: unknown[][] } },
  index = 0,
): [string, RequestInit | undefined] {
  const args = fetchMock.mock.calls[index] as [string, RequestInit | undefined]
  return args
}

const emptyPage = {
  content: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

const summary = {
  id: 7,
  status: 'MAPPING_REQUIRED',
  originalFilename: 'bank-march.csv',
  fileSizeBytes: 2048,
  detectedDelimiter: ',',
  detectedEncoding: 'UTF-8',
  headerRowNumber: 1,
  columns: ['Date', 'Amount'],
  mapping: {},
  totals: { total: 0, valid: 0, invalid: 0, duplicate: 0, created: 0, skipped: 0, failed: 0 },
  errorSummary: null,
  startedAt: null,
  completedAt: null,
  createdAt: '2026-03-09T10:00:00Z',
}

describe('query builders', () => {
  it('history keeps only set filters and always paginates', () => {
    expect(buildHistoryQuery({})).toEqual({ status: undefined, page: 0, size: 50 })
    expect(buildHistoryQuery({ status: 'COMPLETED', page: 2, size: 20 })).toEqual({
      status: 'COMPLETED',
      page: 2,
      size: 20,
    })
  })

  it('preview uses the same contract', () => {
    expect(buildPreviewQuery({ status: 'DUPLICATE', page: 1 })).toEqual({
      status: 'DUPLICATE',
      page: 1,
      size: 50,
    })
  })
})

describe('uploadFormData', () => {
  it('appends the file and only the provided options', () => {
    const file = new File(['date,amount'], 'bank.csv', { type: 'text/csv' })
    const input: UploadImportInput = {
      file,
      defaultCurrencyCode: 'EUR',
      targetAccountId: 4,
    }

    const data = uploadFormData(input)

    expect(data.get('file')).toBeInstanceOf(File)
    expect((data.get('file') as File).name).toBe('bank.csv')
    expect(data.get('defaultCurrencyCode')).toBe('EUR')
    expect(data.get('targetAccountId')).toBe('4')
    expect(data.get('defaultDateFormat')).toBeNull()
    expect(data.get('headerRowNumber')).toBeNull()
  })
})

describe('importsApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists history with the E.12 filter params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(emptyPage))
    vi.stubGlobal('fetch', fetchMock)

    await importsApi.list({ status: 'COMPLETED', page: 1, size: 25 })

    expect(call(fetchMock)[0]).toBe(
      '/api/v1/imports?status=COMPLETED&page=1&size=25',
    )
  })

  it('uploads multipart without a JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(summary))
    vi.stubGlobal('fetch', fetchMock)
    const file = new File(['a,b'], 'x.csv', { type: 'text/csv' })

    await importsApi.upload({ file })

    const [url, init] = call(fetchMock)
    expect(url).toBe('/api/v1/imports')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBeInstanceOf(FormData)
    expect(init?.headers).not.toHaveProperty('Content-Type')
  })

  it('saves the mapping, validates and reads the report', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(summary))
      .mockResolvedValueOnce(jsonResponse(summary))
      .mockResolvedValueOnce(jsonResponse({ import_: summary, createdTransactions: [], failures: [] }))
    vi.stubGlobal('fetch', fetchMock)

    const mapping: SaveMappingInput = {
      columnMappings: { Date: { field: 'date' }, Amount: { field: 'amount' } },
      dateColumn: 'Date',
      amountColumn: 'Amount',
      dateFormat: 'dd/MM/yyyy',
      options: { skipDuplicates: true },
    }
    await importsApi.saveMapping(7, mapping)
    await importsApi.validate(7)
    const report = await importsApi.report(7)

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/imports/7/mapping')
    expect(call(fetchMock, 0)[1]?.method).toBe('PUT')
    expect(JSON.parse(String(call(fetchMock, 0)[1]?.body))).toMatchObject({
      dateColumn: 'Date',
      amountColumn: 'Amount',
      dateFormat: 'dd/MM/yyyy',
      options: { skipDuplicates: true },
    })
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/imports/7/validate')
    expect(call(fetchMock, 1)[1]?.method).toBe('POST')
    expect(report.import_.id).toBe(7)
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/imports/7/report')
  })

  it('previews rows, patches one row and applies decisions', async () => {
    const row = { id: 11, rowNumber: 2, status: 'VALID', errors: [] }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ ...emptyPage, content: [row] }))
      .mockResolvedValueOnce(jsonResponse(row))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    const page = await importsApi.preview(7, { status: 'VALID', page: 3 })
    await importsApi.updateRow(7, 11, { amountMinor: 1250, occurredOn: '2026-03-01' })
    await importsApi.applyDecisions(7, { rowIds: [11], decision: 'SKIP' })

    expect(call(fetchMock, 0)[0]).toBe('/api/v1/imports/7/preview?status=VALID&page=3&size=50')
    expect(page.content[0]?.id).toBe(11)
    expect(call(fetchMock, 1)[0]).toBe('/api/v1/imports/7/rows/11')
    expect(call(fetchMock, 1)[1]?.method).toBe('PATCH')
    expect(JSON.parse(String(call(fetchMock, 1)[1]?.body))).toMatchObject({
      amountMinor: 1250,
      occurredOn: '2026-03-01',
    })
    expect(call(fetchMock, 2)[0]).toBe('/api/v1/imports/7/rows/decisions')
    expect(JSON.parse(String(call(fetchMock, 2)[1]?.body))).toEqual({
      rowIds: [11],
      decision: 'SKIP',
    })
  })

  it('executes with the import options', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ import_: summary, createdTransactions: [], failures: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await importsApi.execute(7, { allowPartialImport: true })

    expect(call(fetchMock)[0]).toBe('/api/v1/imports/7/execute')
    expect(call(fetchMock)[1]?.method).toBe('POST')
    expect(JSON.parse(String(call(fetchMock)[1]?.body))).toEqual({
      allowPartialImport: true,
    })
  })

  it('deletes without a body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await importsApi.remove(7)

    expect(call(fetchMock)[0]).toBe('/api/v1/imports/7')
    expect(call(fetchMock)[1]?.method).toBe('DELETE')
  })

  it('downloads the errors CSV and reads the filename', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('row,reason\n3,missing account', {
        status: 200,
        headers: { 'Content-Disposition': 'attachment; filename="import-7-errors.csv"' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const { blob, filename } = await importsApi.errorsCsv(7)

    expect(call(fetchMock)[0]).toBe('/api/v1/imports/7/errors.csv')
    expect(filename).toBe('import-7-errors.csv')
    expect(await blob.text()).toContain('missing account')
  })
})
