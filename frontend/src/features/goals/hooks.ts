import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { goalsApi } from './api'
import type {
  ChartGranularity,
  CreateContributionInput,
  CreateGoalInput,
  Goal,
  GoalContributionPage,
  GoalFilter,
  GoalPage,
  GoalProgress,
  GoalSummary,
  UpdateGoalInput,
} from './types'

export const goalKeys = {
  all: ['goals'] as const,
  list: (filter: GoalFilter) => ['goals', 'list', filter] as const,
  summary: ['goals', 'summary'] as const,
  detail: (id: number) => ['goals', 'detail', id] as const,
  progress: (id: number, granularity?: ChartGranularity) =>
    ['goals', 'progress', id, granularity ?? ''] as const,
  contributions: (id: number, page: number) => ['goals', 'contributions', id, page] as const,
}

function invalidateGoals(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: goalKeys.all })
}

export function useGoals(filter: GoalFilter) {
  return useQuery<GoalPage>({
    queryKey: goalKeys.list(filter),
    queryFn: () => goalsApi.list(filter),
  })
}

export function useGoalSummary() {
  return useQuery<GoalSummary>({
    queryKey: goalKeys.summary,
    queryFn: goalsApi.summary,
  })
}

export function useGoal(id: number) {
  return useQuery<Goal>({
    queryKey: goalKeys.detail(id),
    queryFn: () => goalsApi.detail(id),
    enabled: id > 0,
  })
}

export function useGoalProgress(id: number, granularity?: ChartGranularity) {
  return useQuery<GoalProgress>({
    queryKey: goalKeys.progress(id, granularity),
    queryFn: () => goalsApi.progress(id, granularity),
    enabled: id > 0,
  })
}

export function useGoalContributions(id: number, page = 0, size = 20) {
  return useQuery<GoalContributionPage>({
    queryKey: goalKeys.contributions(id, page),
    queryFn: () => goalsApi.contributions(id, page, size),
    enabled: id > 0,
  })
}

export function useCreateGoal() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateGoalInput) => goalsApi.create(input),
    onSuccess: (goal) => {
      invalidateGoals(client)
      client.setQueryData<Goal>(goalKeys.detail(goal.id), goal)
    },
  })
}

export function useUpdateGoal() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: UpdateGoalInput }) =>
      goalsApi.update(id, input),
    onSuccess: (goal) => {
      invalidateGoals(client)
      client.setQueryData<Goal>(goalKeys.detail(goal.id), goal)
    },
  })
}

export function useDeleteGoal() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => goalsApi.remove(id),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: goalKeys.detail(id) })
      invalidateGoals(client)
    },
  })
}

export function useCompleteGoal() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => goalsApi.complete(id),
    onSuccess: (goal) => {
      invalidateGoals(client)
      client.setQueryData<Goal>(goalKeys.detail(goal.id), goal)
    },
  })
}

export function useReopenGoal() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => goalsApi.reopen(id),
    onSuccess: (goal) => {
      invalidateGoals(client)
      client.setQueryData<Goal>(goalKeys.detail(goal.id), goal)
    },
  })
}

export function useAddContribution() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ goalId, input }: { goalId: number; input: CreateContributionInput }) =>
      goalsApi.addContribution(goalId, input),
    onSuccess: (contribution) => {
      invalidateGoals(client)
      void client.invalidateQueries({
        queryKey: goalKeys.contributions(contribution.goalId, 0),
      })
    },
  })
}

export function useRemoveContribution() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ goalId, contributionId }: { goalId: number; contributionId: number }) =>
      goalsApi.removeContribution(goalId, contributionId),
    onSuccess: (_data, variables) => {
      invalidateGoals(client)
      void client.invalidateQueries({ queryKey: goalKeys.contributions(variables.goalId, 0) })
    },
  })
}