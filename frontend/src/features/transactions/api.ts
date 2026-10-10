import { toApiError } from '@/services/http/ApiError'
import { buildUrl, http, type QueryValue } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import { getAccessToken } from '@/services/http/tokenManager'
import type {
  BulkUpdateResult,
  CreateTransactionInput,
  CreateTransferInput,
  DuplicateCheck,
  ReverseTransactionInput,
  Transaction,
  TransactionFilter,
  TransactionFilterOptions,
  TransactionPage,
  TransactionSummary,
  Transfer,
  UpdateTransactionInput,
} from './types'

/** Serialises the ledger filter into the backend's query contract (E.6). */
export function buildTransactionQuery(filter: TransactionFilter): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {
    q: filter.q,
    accountId: filter.accountIds,
    categoryId: filter.categoryIds,
    categoryType: filter.categoryType,
    type: filter.types,
    direction: filter.directions,
    status: filter.statuses,
    from: filter.from,
    to: filter.to,
    minAmountMinor: filter.minAmountMinor,
    maxAmountMinor: filter.maxAmountMinor,
    currencyCode: filter.currencyCode,
    baseCurrencyOnly: filter.baseCurrencyOnly,
    recurringOnly: filter.recurringOnly,
    importedOnly: filter.importedOnly,
    hasNotes: filter.hasNotes,
    hasReversal: filter.hasReversal,
    sort: filter.sort,
  }
  if (filter.page !== undefined) query.page = filter.page
  if (filter.size !== undefined) query.size = filter.size
  return query
}

export const transactionsApi = {
  list(filter: TransactionFilter): Promise<TransactionPage> {
    return http.get<TransactionPage>(endpoints.transactions.list, {
      query: { ...buildTransactionQuery(filter), page: filter.page ?? 0, size: filter.size ?? 50 },
    })
  },

  summary(filter: TransactionFilter): Promise<TransactionSummary> {
    const { page: _page, size: _size, sort: _sort, ...rest } = filter
    return http.get<TransactionSummary>(endpoints.transactions.summary, {
      query: buildTransactionQuery(rest),
    })
  },

  options(): Promise<TransactionFilterOptions> {
    return http.get<TransactionFilterOptions>(endpoints.transactions.options)
  },

  detail(id: number): Promise<Transaction> {
    return http.get<Transaction>(endpoints.transactions.detail(String(id)))
  },

  create(input: CreateTransactionInput, idempotencyKey: string): Promise<Transaction> {
    return http.post<Transaction>(endpoints.transactions.list, input, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
  },

  update(id: number, input: UpdateTransactionInput): Promise<Transaction> {
    return http.patch<Transaction>(endpoints.transactions.update(String(id)), input)
  },

  remove(id: number): Promise<void> {
    return http.delete<void>(endpoints.transactions.remove(String(id)))
  },

  reverse(id: number, input: ReverseTransactionInput): Promise<Transaction> {
    return http.post<Transaction>(endpoints.transactions.reverse(String(id)), input)
  },

  recategorise(ids: number[], categoryId: number): Promise<BulkUpdateResult> {
    return http.post<BulkUpdateResult>(endpoints.transactions.bulk, { ids, categoryId })
  },

  bulkDelete(ids: number[]): Promise<BulkUpdateResult> {
    return http.delete<BulkUpdateResult>(endpoints.transactions.bulk, { body: { ids } })
  },

  checkDuplicate(input: {
    accountId: number
    occurredOn: string
    amountMinor: number
    currencyCode: string
    description?: string
  }): Promise<DuplicateCheck> {
    return http.post<DuplicateCheck>(endpoints.transactions.checkDuplicate, input)
  },

  /**
   * Downloads the CSV for the current filter. The shared client only speaks
   * JSON, so the export goes through `fetch` and returns the file to save.
   */
  async exportCsv(filter: TransactionFilter): Promise<{ blob: Blob; filename: string }> {
    const token = getAccessToken()
    const response = await fetch(
      buildUrl(endpoints.transactions.export, buildTransactionQuery(filter)),
      {
        method: 'GET',
        headers: {
          Accept: 'text/csv',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      },
    )
    if (!response.ok) throw await toApiError(response)
    const disposition = response.headers.get('Content-Disposition') ?? ''
    const match = /filename="([^"]+)"/.exec(disposition)
    return {
      blob: await response.blob(),
      filename: match?.[1] ?? 'spendio-transactions.csv',
    }
  },
}

export const transfersApi = {
  create(input: CreateTransferInput): Promise<Transfer> {
    return http.post<Transfer>(endpoints.transfers.create, input)
  },

  reverse(transferGroupId: string, input: ReverseTransactionInput): Promise<Transfer> {
    return http.post<Transfer>(endpoints.transfers.reverse(transferGroupId), input)
  },
}
