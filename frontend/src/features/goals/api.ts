import { http, type QueryValue } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  ChartGranularity,
  CreateContributionInput,
  CreateGoalInput,
  Goal,
  GoalContribution,
  GoalContributionPage,
  GoalFilter,
  GoalPage,
  GoalProgress,
  GoalSummary,
  UpdateGoalInput,
} from './types'

export function buildGoalQuery(filter: GoalFilter): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {
    status: filter.status,
    q: filter.q,
  }
  if (filter.page !== undefined) query.page = filter.page
  if (filter.size !== undefined) query.size = filter.size
  return query
}

export const goalsApi = {
  list(filter: GoalFilter): Promise<GoalPage> {
    return http.get<GoalPage>(endpoints.goals.list, {
      query: { ...buildGoalQuery(filter), page: filter.page ?? 0, size: filter.size ?? 50 },
    })
  },

  create(input: CreateGoalInput): Promise<Goal> {
    return http.post<Goal>(endpoints.goals.create, input)
  },

  summary(): Promise<GoalSummary> {
    return http.get<GoalSummary>(endpoints.goals.summary)
  },

  detail(id: number): Promise<Goal> {
    return http.get<Goal>(endpoints.goals.detail(String(id)))
  },

  update(id: number, input: UpdateGoalInput): Promise<Goal> {
    return http.patch<Goal>(endpoints.goals.update(String(id)), input)
  },

  remove(id: number): Promise<void> {
    return http.delete<void>(endpoints.goals.remove(String(id)))
  },

  complete(id: number): Promise<Goal> {
    return http.post<Goal>(endpoints.goals.complete(String(id)))
  },

  reopen(id: number): Promise<Goal> {
    return http.post<Goal>(endpoints.goals.reopen(String(id)))
  },

  progress(id: number, granularity?: ChartGranularity): Promise<GoalProgress> {
    return http.get<GoalProgress>(endpoints.goals.progress(String(id)), {
      query: { granularity },
    })
  },

  contributions(id: number, page = 0, size = 20): Promise<GoalContributionPage> {
    return http.get<GoalContributionPage>(endpoints.goals.contributions(String(id)), {
      query: { page, size },
    })
  },

  addContribution(goalId: number, input: CreateContributionInput): Promise<GoalContribution> {
    return http.post<GoalContribution>(endpoints.goals.contributions(String(goalId)), input)
  },

  removeContribution(goalId: number, contributionId: number): Promise<void> {
    return http.delete<void>(endpoints.goals.contribution(String(goalId), String(contributionId)))
  },
}