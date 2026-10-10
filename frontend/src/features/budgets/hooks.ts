import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { budgetsApi } from './api'
import type {
  Budget,
  BudgetAlertFilter,
  BudgetAlertPage,
  BudgetFilter,
  BudgetHistory,
  BudgetPage,
  BudgetProgress,
  CreateBudgetInput,
  UpdateBudgetInput,
} from './types'

export const budgetKeys = {
  all: ['budgets'] as const,
  list: (filter: BudgetFilter) => ['budgets', 'list', filter] as const,
  current: ['budgets', 'current'] as const,
  alerts: (filter: BudgetAlertFilter) => ['budgets', 'alerts', filter] as const,
  detail: (id: number) => ['budgets', 'detail', id] as const,
  progress: (id: number, start?: string, end?: string) =>
    ['budgets', 'progress', id, start ?? '', end ?? ''] as const,
  history: (id: number) => ['budgets', 'history', id] as const,
}

function invalidateBudgets(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: budgetKeys.all })
}

export function useBudgets(filter: BudgetFilter) {
  return useQuery<BudgetPage>({
    queryKey: budgetKeys.list(filter),
    queryFn: () => budgetsApi.list(filter),
  })
}

export function useCurrentBudgets() {
  return useQuery<BudgetProgress[]>({
    queryKey: budgetKeys.current,
    queryFn: budgetsApi.current,
  })
}

export function useBudgetAlerts(filter: BudgetAlertFilter) {
  return useQuery<BudgetAlertPage>({
    queryKey: budgetKeys.alerts(filter),
    queryFn: () => budgetsApi.alerts(filter),
  })
}

export function useBudget(id: number) {
  return useQuery<Budget>({
    queryKey: budgetKeys.detail(id),
    queryFn: () => budgetsApi.detail(id),
    enabled: id > 0,
  })
}

export function useBudgetProgress(id: number, periodStart?: string, periodEnd?: string) {
  return useQuery<BudgetProgress>({
    queryKey: budgetKeys.progress(id, periodStart, periodEnd),
    queryFn: () => budgetsApi.progress(id, periodStart, periodEnd),
    enabled: id > 0,
  })
}

export function useBudgetHistory(id: number) {
  return useQuery<BudgetHistory>({
    queryKey: budgetKeys.history(id),
    queryFn: () => budgetsApi.history(id),
    enabled: id > 0,
  })
}

export function useCreateBudget() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateBudgetInput) => budgetsApi.create(input),
    onSuccess: (budget) => {
      invalidateBudgets(client)
      client.setQueryData<Budget>(budgetKeys.detail(budget.id), budget)
      void client.invalidateQueries({ queryKey: budgetKeys.current })
    },
  })
}

export function useUpdateBudget() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: UpdateBudgetInput }) =>
      budgetsApi.update(id, input),
    onSuccess: (budget) => {
      invalidateBudgets(client)
      client.setQueryData<Budget>(budgetKeys.detail(budget.id), budget)
      void client.invalidateQueries({ queryKey: budgetKeys.current })
    },
  })
}

export function useDeleteBudget() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => budgetsApi.remove(id),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: budgetKeys.detail(id) })
      invalidateBudgets(client)
      void client.invalidateQueries({ queryKey: budgetKeys.current })
    },
  })
}

export function useAcknowledgeBudgetAlert() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ budgetId, alertId }: { budgetId: number; alertId: number }) =>
      budgetsApi.acknowledge(budgetId, alertId),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: budgetKeys.all })
    },
  })
}

export function useEvaluateBudgets() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (window?: { periodStart?: string; periodEnd?: string }) =>
      budgetsApi.evaluate(window?.periodStart, window?.periodEnd),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: budgetKeys.all })
    },
  })
}