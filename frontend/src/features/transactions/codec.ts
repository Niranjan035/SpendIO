import type { TransactionDirection, TransactionStatus, TransactionType } from '@/types/enums'
import type { TransactionFilter } from './types'

/**
 * URL codec for the ledger filter (ARCHITECTURE.md Phase 6): the address bar is
 * the source of truth for shareable, bookmarkable searches. Repeated params
 * carry the multi-value filters, and anything unset is simply absent.
 */

export const FILTER_PARAMS = {
  q: 'q',
  accountIds: 'accountId',
  categoryIds: 'categoryId',
  categoryType: 'categoryType',
  types: 'type',
  directions: 'direction',
  statuses: 'status',
  from: 'from',
  to: 'to',
  minAmountMinor: 'minAmountMinor',
  maxAmountMinor: 'maxAmountMinor',
  currencyCode: 'currencyCode',
  baseCurrencyOnly: 'baseCurrencyOnly',
  recurringOnly: 'recurringOnly',
  importedOnly: 'importedOnly',
  hasNotes: 'hasNotes',
  hasReversal: 'hasReversal',
  sort: 'sort',
  page: 'page',
  size: 'size',
} as const

function readNumber(raw: string | null): number | undefined {
  if (raw === null || raw.trim() === '') return undefined
  const value = Number(raw)
  return Number.isFinite(value) ? value : undefined
}

function readInteger(raw: string | null): number | undefined {
  const value = readNumber(raw)
  return value === undefined || !Number.isInteger(value) ? undefined : value
}

function readBoolean(raw: string | null): boolean | undefined {
  if (raw === 'true') return true
  if (raw === 'false') return false
  return undefined
}

function readNumbers(params: URLSearchParams, key: string): number[] | undefined {
  const values = params
    .getAll(key)
    .map((raw) => Number(raw))
    .filter((value) => Number.isFinite(value))
  return values.length > 0 ? values : undefined
}

function readEnums<T extends string>(
  params: URLSearchParams,
  key: string,
  allowed: readonly T[],
): T[] | undefined {
  const values = params
    .getAll(key)
    .filter((value): value is T => (allowed as readonly string[]).includes(value))
  return values.length > 0 ? values : undefined
}

export function parseFilter(params: URLSearchParams): TransactionFilter {
  const filter: TransactionFilter = {}

  const q = params.get(FILTER_PARAMS.q)
  if (q) filter.q = q

  const accountIds = readNumbers(params, FILTER_PARAMS.accountIds)
  if (accountIds) filter.accountIds = accountIds

  const categoryIds = readNumbers(params, FILTER_PARAMS.categoryIds)
  if (categoryIds) filter.categoryIds = categoryIds

  const categoryType = params.get(FILTER_PARAMS.categoryType)
  if (categoryType === 'INCOME' || categoryType === 'EXPENSE') filter.categoryType = categoryType

  const types = readEnums<TransactionType>(params, FILTER_PARAMS.types, [
    'INCOME',
    'EXPENSE',
    'ADJUSTMENT',
    'TRANSFER',
  ])
  if (types) filter.types = types

  const directions = readEnums<TransactionDirection>(params, FILTER_PARAMS.directions, [
    'INFLOW',
    'OUTFLOW',
  ])
  if (directions) filter.directions = directions

  const statuses = readEnums<TransactionStatus>(params, FILTER_PARAMS.statuses, [
    'POSTED',
    'PENDING',
    'VOID',
  ])
  if (statuses) filter.statuses = statuses

  const from = params.get(FILTER_PARAMS.from)
  if (from) filter.from = from

  const to = params.get(FILTER_PARAMS.to)
  if (to) filter.to = to

  const minAmountMinor = readInteger(params.get(FILTER_PARAMS.minAmountMinor))
  if (minAmountMinor !== undefined) filter.minAmountMinor = minAmountMinor

  const maxAmountMinor = readInteger(params.get(FILTER_PARAMS.maxAmountMinor))
  if (maxAmountMinor !== undefined) filter.maxAmountMinor = maxAmountMinor

  const currencyCode = params.get(FILTER_PARAMS.currencyCode)
  if (currencyCode) filter.currencyCode = currencyCode

  const baseCurrencyOnly = readBoolean(params.get(FILTER_PARAMS.baseCurrencyOnly))
  if (baseCurrencyOnly !== undefined) filter.baseCurrencyOnly = baseCurrencyOnly

  const recurringOnly = readBoolean(params.get(FILTER_PARAMS.recurringOnly))
  if (recurringOnly !== undefined) filter.recurringOnly = recurringOnly

  const importedOnly = readBoolean(params.get(FILTER_PARAMS.importedOnly))
  if (importedOnly !== undefined) filter.importedOnly = importedOnly

  const hasNotes = readBoolean(params.get(FILTER_PARAMS.hasNotes))
  if (hasNotes !== undefined) filter.hasNotes = hasNotes

  const hasReversal = readBoolean(params.get(FILTER_PARAMS.hasReversal))
  if (hasReversal !== undefined) filter.hasReversal = hasReversal

  const sort = params.get(FILTER_PARAMS.sort)
  if (sort) filter.sort = sort

  const page = readInteger(params.get(FILTER_PARAMS.page))
  if (page !== undefined) filter.page = page

  const size = readInteger(params.get(FILTER_PARAMS.size))
  if (size !== undefined) filter.size = size

  return filter
}

export function serializeFilter(filter: TransactionFilter): URLSearchParams {
  const params = new URLSearchParams()

  if (filter.q) params.set(FILTER_PARAMS.q, filter.q)
  for (const id of filter.accountIds ?? []) params.append(FILTER_PARAMS.accountIds, String(id))
  for (const id of filter.categoryIds ?? []) params.append(FILTER_PARAMS.categoryIds, String(id))
  if (filter.categoryType) params.set(FILTER_PARAMS.categoryType, filter.categoryType)
  for (const value of filter.types ?? []) params.append(FILTER_PARAMS.types, value)
  for (const value of filter.directions ?? []) params.append(FILTER_PARAMS.directions, value)
  for (const value of filter.statuses ?? []) params.append(FILTER_PARAMS.statuses, value)
  if (filter.from) params.set(FILTER_PARAMS.from, filter.from)
  if (filter.to) params.set(FILTER_PARAMS.to, filter.to)
  if (filter.minAmountMinor !== undefined) {
    params.set(FILTER_PARAMS.minAmountMinor, String(filter.minAmountMinor))
  }
  if (filter.maxAmountMinor !== undefined) {
    params.set(FILTER_PARAMS.maxAmountMinor, String(filter.maxAmountMinor))
  }
  if (filter.currencyCode) params.set(FILTER_PARAMS.currencyCode, filter.currencyCode)
  if (filter.baseCurrencyOnly !== undefined) {
    params.set(FILTER_PARAMS.baseCurrencyOnly, String(filter.baseCurrencyOnly))
  }
  if (filter.recurringOnly !== undefined) {
    params.set(FILTER_PARAMS.recurringOnly, String(filter.recurringOnly))
  }
  if (filter.importedOnly !== undefined) {
    params.set(FILTER_PARAMS.importedOnly, String(filter.importedOnly))
  }
  if (filter.hasNotes !== undefined) params.set(FILTER_PARAMS.hasNotes, String(filter.hasNotes))
  if (filter.hasReversal !== undefined) {
    params.set(FILTER_PARAMS.hasReversal, String(filter.hasReversal))
  }
  if (filter.sort) params.set(FILTER_PARAMS.sort, filter.sort)
  if (filter.page !== undefined) params.set(FILTER_PARAMS.page, String(filter.page))
  if (filter.size !== undefined) params.set(FILTER_PARAMS.size, String(filter.size))

  return params
}
