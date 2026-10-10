import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { recurringApi } from './api'
import type {
  CreateRecurringInput,
  OccurrencePreview,
  RecurringFilter,
  RecurringRunPage,
  RecurringTransaction,
  UpdateRecurringInput,
} from './types'

export const recurringKeys = {
  all: ['recurring'] as const,
  list: (filter: RecurringFilter) => ['recurring', 'list', filter] as const,
  upcoming: (days?: number) => ['recurring', 'upcoming', days ?? 0] as const,
  detail: (id: number) => ['recurring', 'detail', id] as const,
  preview: (id: number, count: number) => ['recurring', 'preview', id, count] as const,
  runs: (id: number, page: number) => ['recurring', 'runs', id, page] as const,
}

function invalidateRecurring(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: recurringKeys.all })
}

export function useRecurrings(filter: RecurringFilter) {
  return useQuery({
    queryKey: recurringKeys.list(filter),
    queryFn: () => recurringApi.list(filter),
  })
}

export function useUpcomingRecurrings(days = 30) {
  return useQuery({
    queryKey: recurringKeys.upcoming(days),
    queryFn: () => recurringApi.upcoming(days),
  })
}

export function useRecurring(id: number) {
  return useQuery<RecurringTransaction>({
    queryKey: recurringKeys.detail(id),
    queryFn: () => recurringApi.detail(id),
    enabled: id > 0,
  })
}

export function useRecurringPreview(id: number, count = 5) {
  return useQuery<OccurrencePreview>({
    queryKey: recurringKeys.preview(id, count),
    queryFn: () => recurringApi.preview(id, count),
    enabled: id > 0,
  })
}

export function useRecurringRuns(id: number, page = 0) {
  return useQuery<RecurringRunPage>({
    queryKey: recurringKeys.runs(id, page),
    queryFn: () => recurringApi.runs(id, page),
    enabled: id > 0,
  })
}

export function useCreateRecurring() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateRecurringInput) => recurringApi.create(input),
    onSuccess: (result) => {
      invalidateRecurring(client)
      client.setQueryData<RecurringTransaction>(recurringKeys.detail(result.rule.id), result.rule)
    },
  })
}

export function useUpdateRecurring() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: UpdateRecurringInput }) =>
      recurringApi.update(id, input),
    onSuccess: (rule) => {
      invalidateRecurring(client)
      client.setQueryData<RecurringTransaction>(recurringKeys.detail(rule.id), rule)
      void client.invalidateQueries({ queryKey: recurringKeys.upcoming() })
    },
  })
}

export function useDeleteRecurring() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => recurringApi.remove(id),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: recurringKeys.detail(id) })
      invalidateRecurring(client)
    },
  })
}

export function usePauseRecurring() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => recurringApi.pause(id),
    onSuccess: (rule) => {
      invalidateRecurring(client)
      client.setQueryData<RecurringTransaction>(recurringKeys.detail(rule.id), rule)
      void client.invalidateQueries({ queryKey: recurringKeys.upcoming() })
    },
  })
}

export function useResumeRecurring() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => recurringApi.resume(id),
    onSuccess: (rule) => {
      invalidateRecurring(client)
      client.setQueryData<RecurringTransaction>(recurringKeys.detail(rule.id), rule)
      void client.invalidateQueries({ queryKey: recurringKeys.upcoming() })
    },
  })
}

export function useCancelRecurring() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => recurringApi.cancel(id),
    onSuccess: (rule) => {
      client.removeQueries({ queryKey: recurringKeys.detail(rule.id) })
      invalidateRecurring(client)
    },
  })
}

export function useRunNowRecurring() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, idempotencyKey }: { id: number; idempotencyKey: string }) =>
      recurringApi.runNow(id, idempotencyKey),
    onSuccess: (_run, variables) => {
      invalidateRecurring(client)
      void client.invalidateQueries({ queryKey: recurringKeys.detail(variables.id) })
      void client.invalidateQueries({ queryKey: recurringKeys.runs(variables.id, 0) })
      void client.invalidateQueries({ queryKey: recurringKeys.upcoming() })
    },
  })
}