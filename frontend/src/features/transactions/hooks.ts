import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { transactionsApi, transfersApi } from './api'
import type {
  CreateTransactionInput,
  CreateTransferInput,
  ReverseTransactionInput,
  Transaction,
  TransactionFilter,
  TransactionFilterOptions,
  TransactionSummary,
  UpdateTransactionInput,
} from './types'

export const transactionsKeys = {
  all: ['transactions'] as const,
  list: (filter: TransactionFilter) => ['transactions', 'list', filter] as const,
  summary: (filter: TransactionFilter) => ['transactions', 'summary', filter] as const,
  options: ['transactions', 'options'] as const,
  detail: (id: number) => ['transactions', 'detail', id] as const,
}

export const transfersKeys = {
  all: ['transfers'] as const,
}

function invalidateTransactions(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: transactionsKeys.all })
}

function invalidateTransfers(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: transfersKeys.all })
}

export function useTransactions(filter: TransactionFilter) {
  return useQuery({
    queryKey: transactionsKeys.list(filter),
    queryFn: () => transactionsApi.list(filter),
  })
}

export function useTransactionSummary(filter: TransactionFilter) {
  return useQuery<TransactionSummary>({
    queryKey: transactionsKeys.summary(filter),
    queryFn: () => transactionsApi.summary(filter),
  })
}

export function useTransactionFilterOptions() {
  return useQuery<TransactionFilterOptions>({
    queryKey: transactionsKeys.options,
    queryFn: () => transactionsApi.options(),
  })
}

export function useTransaction(id: number) {
  return useQuery<Transaction>({
    queryKey: transactionsKeys.detail(id),
    queryFn: () => transactionsApi.detail(id),
    enabled: id > 0,
  })
}

export function useCreateTransaction() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({
      input,
      idempotencyKey,
    }: {
      input: CreateTransactionInput
      idempotencyKey: string
    }) => transactionsApi.create(input, idempotencyKey),
    onSuccess: () => invalidateTransactions(client),
  })
}

export function useUpdateTransaction() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: UpdateTransactionInput }) =>
      transactionsApi.update(id, input),
    onSuccess: (transaction) => {
      invalidateTransactions(client)
      client.setQueryData<Transaction>(transactionsKeys.detail(transaction.id), transaction)
    },
  })
}

export function useDeleteTransaction() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => transactionsApi.remove(id),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: transactionsKeys.detail(id) })
      invalidateTransactions(client)
    },
  })
}

export function useCreateTransfer() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateTransferInput) => transfersApi.create(input),
    onSuccess: () => {
      invalidateTransfers(client)
      invalidateTransactions(client)
    },
  })
}

export function useReverseTransaction() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: ReverseTransactionInput }) =>
      transactionsApi.reverse(id, input),
    onSuccess: () => invalidateTransactions(client),
  })
}

export function useReverseTransfer() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({
      transferGroupId,
      input,
    }: {
      transferGroupId: string
      input: ReverseTransactionInput
    }) => transfersApi.reverse(transferGroupId, input),
    onSuccess: () => {
      invalidateTransfers(client)
      invalidateTransactions(client)
    },
  })
}
