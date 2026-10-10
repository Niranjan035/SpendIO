import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { transactionsKeys } from '@/features/transactions/hooks'
import { analyticsKeys } from '@/features/analytics/hooks'
import { accountsApi } from './api'
import type {
  Account,
  AccountBalance,
  AccountFilter,
  AccountTotals,
  AdjustBalanceInput,
  CreateAccountInput,
  DailyBalancePoint,
  UpdateAccountInput,
} from './types'

export const accountsKeys = {
  all: ['accounts'] as const,
  list: (filter: AccountFilter) => ['accounts', 'list', filter] as const,
  detail: (id: number) => ['accounts', 'detail', id] as const,
  summary: ['accounts', 'summary'] as const,
  balance: (id: number) => ['accounts', 'balance', id] as const,
  balances: (id: number, from: string, to: string) =>
    ['accounts', 'balances', id, from, to] as const,
}

function invalidateAccounts(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: accountsKeys.all })
}

export function useAccounts(filter: AccountFilter) {
  return useQuery({
    queryKey: accountsKeys.list(filter),
    queryFn: () => accountsApi.list(filter),
  })
}

export function useAccountSummary() {
  return useQuery<AccountTotals>({
    queryKey: accountsKeys.summary,
    queryFn: () => accountsApi.summary(),
  })
}

export function useAccount(id: number) {
  return useQuery<Account>({
    queryKey: accountsKeys.detail(id),
    queryFn: () => accountsApi.detail(id),
    enabled: id > 0,
  })
}

export function useAccountBalance(id: number) {
  return useQuery<AccountBalance>({
    queryKey: accountsKeys.balance(id),
    queryFn: () => accountsApi.balance(id),
    enabled: id > 0,
  })
}

export function useDailyBalances(id: number, from: string, to: string) {
  return useQuery<DailyBalancePoint[]>({
    queryKey: accountsKeys.balances(id, from, to),
    queryFn: () => accountsApi.balances(id, from, to),
    enabled: id > 0 && from.length > 0 && to.length > 0,
  })
}

export function useCreateAccount() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateAccountInput) => accountsApi.create(input),
    onSuccess: () => invalidateAccounts(client),
  })
}

export function useUpdateAccount() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: UpdateAccountInput }) =>
      accountsApi.update(id, input),
    onSuccess: (account) => {
      invalidateAccounts(client)
      client.setQueryData<Account>(accountsKeys.detail(account.id), account)
      void client.invalidateQueries({ queryKey: transactionsKeys.all })
      void client.invalidateQueries({ queryKey: analyticsKeys.all })
      void client.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}

export function useDeleteAccount() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, force }: { id: number; force?: boolean }) =>
      accountsApi.remove(id, force ?? false),
    onSuccess: (_data, variables) => {
      client.removeQueries({ queryKey: accountsKeys.detail(variables.id) })
      invalidateAccounts(client)
    },
  })
}

export function useArchiveAccount() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => accountsApi.archive(id),
    onSuccess: (account) => {
      invalidateAccounts(client)
      client.setQueryData<Account>(accountsKeys.detail(account.id), account)
    },
  })
}

export function useRestoreAccount() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => accountsApi.restore(id),
    onSuccess: (account) => {
      invalidateAccounts(client)
      client.setQueryData<Account>(accountsKeys.detail(account.id), account)
    },
  })
}

export function useSetPrimaryAccount() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => accountsApi.setPrimary(id),
    onSuccess: (account) => {
      invalidateAccounts(client)
      client.setQueryData<Account>(accountsKeys.detail(account.id), account)
    },
  })
}

export function useAdjustBalance() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: AdjustBalanceInput }) =>
      accountsApi.adjust(id, input),
    onSuccess: (_balance, variables) => {
      invalidateAccounts(client)
      void client.invalidateQueries({ queryKey: accountsKeys.balance(variables.id) })
    },
  })
}
