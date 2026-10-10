import { http } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  Account,
  AccountBalance,
  AccountFilter,
  AccountPage,
  AccountTotals,
  AdjustBalanceInput,
  CreateAccountInput,
  DailyBalancePoint,
  UpdateAccountInput,
} from './types'

export function buildAccountQuery(
  filter: AccountFilter,
): Record<string, string | number | boolean> {
  return {
    type: filter.type ?? '',
    status: filter.status ?? '',
    currencyCode: filter.currencyCode ?? '',
    q: filter.q ?? '',
    includeArchived: filter.includeArchived ?? false,
    hasTransactions: filter.hasTransactions ?? false,
    sort: filter.sort ?? '',
    page: filter.page ?? 0,
    size: filter.size ?? 50,
  }
}

export const accountsApi = {
  list(filter: AccountFilter): Promise<AccountPage> {
    return http.get<AccountPage>(endpoints.accounts.list, { query: buildAccountQuery(filter) })
  },

  create(input: CreateAccountInput): Promise<Account> {
    return http.post<Account>(endpoints.accounts.create, input)
  },

  summary(): Promise<AccountTotals> {
    return http.get<AccountTotals>(endpoints.accounts.summary)
  },

  detail(id: number): Promise<Account> {
    return http.get<Account>(endpoints.accounts.detail(String(id)))
  },

  update(id: number, input: UpdateAccountInput): Promise<Account> {
    return http.patch<Account>(endpoints.accounts.update(String(id)), input)
  },

  remove(id: number, force = false): Promise<void> {
    return http.delete<void>(endpoints.accounts.remove(String(id)), { query: { force } })
  },

  archive(id: number): Promise<Account> {
    return http.post<Account>(endpoints.accounts.archive(String(id)))
  },

  restore(id: number): Promise<Account> {
    return http.post<Account>(endpoints.accounts.restore(String(id)))
  },

  setPrimary(id: number): Promise<Account> {
    return http.post<Account>(endpoints.accounts.setPrimary(String(id)))
  },

  balance(id: number): Promise<AccountBalance> {
    return http.get<AccountBalance>(endpoints.accounts.balance(String(id)))
  },

  balances(id: number, from: string, to: string): Promise<DailyBalancePoint[]> {
    return http.get<DailyBalancePoint[]>(endpoints.accounts.balances(String(id)), {
      query: { from, to },
    })
  },

  adjust(id: number, input: AdjustBalanceInput): Promise<AccountBalance> {
    return http.post<AccountBalance>(endpoints.accounts.adjust(String(id)), input)
  },
}
