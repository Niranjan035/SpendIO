import { http, type QueryValue } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  Budget,
  BudgetAlertFilter,
  BudgetAlertPage,
  BudgetFilter,
  BudgetHistory,
  BudgetPage,
  BudgetProgress,
  CreateBudgetInput,
  EvaluateResult,
  UpdateBudgetInput,
} from './types'

/** Serialises the budget filter into the backend query contract (E.10). */
export function buildBudgetQuery(filter: BudgetFilter): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {
    status: filter.status,
    periodType: filter.periodType,
    categoryId: filter.categoryId,
    activeInPeriod: filter.activeInPeriod,
  }
  if (filter.page !== undefined) query.page = filter.page
  if (filter.size !== undefined) query.size = filter.size
  return query
}

export function buildAlertQuery(filter: BudgetAlertFilter): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {
    unacknowledgedOnly: filter.unacknowledgedOnly,
    from: filter.from,
    to: filter.to,
  }
  if (filter.page !== undefined) query.page = filter.page
  if (filter.size !== undefined) query.size = filter.size
  return query
}

export const budgetsApi = {
  list(filter: BudgetFilter): Promise<BudgetPage> {
    return http.get<BudgetPage>(endpoints.budgets.list, {
      query: { ...buildBudgetQuery(filter), page: filter.page ?? 0, size: filter.size ?? 50 },
    })
  },

  create(input: CreateBudgetInput): Promise<Budget> {
    return http.post<Budget>(endpoints.budgets.create, input)
  },

  current(): Promise<BudgetProgress[]> {
    return http.get<BudgetProgress[]>(endpoints.budgets.current)
  },

  alerts(filter: BudgetAlertFilter): Promise<BudgetAlertPage> {
    return http.get<BudgetAlertPage>(endpoints.budgets.alerts, {
      query: { ...buildAlertQuery(filter), page: filter.page ?? 0, size: filter.size ?? 50 },
    })
  },

  evaluate(periodStart?: string, periodEnd?: string): Promise<EvaluateResult> {
    return http.post<EvaluateResult>(endpoints.budgets.evaluate, undefined, {
      query: { periodStart, periodEnd },
    })
  },

  detail(id: number): Promise<Budget> {
    return http.get<Budget>(endpoints.budgets.detail(String(id)))
  },

  update(id: number, input: UpdateBudgetInput): Promise<Budget> {
    return http.patch<Budget>(endpoints.budgets.update(String(id)), input)
  },

  remove(id: number): Promise<void> {
    return http.delete<void>(endpoints.budgets.remove(String(id)))
  },

  progress(id: number, periodStart?: string, periodEnd?: string): Promise<BudgetProgress> {
    return http.get<BudgetProgress>(endpoints.budgets.progress(String(id)), {
      query: { periodStart, periodEnd },
    })
  },

  history(id: number): Promise<BudgetHistory> {
    return http.get<BudgetHistory>(endpoints.budgets.history(String(id)))
  },

  acknowledge(id: number, alertId: number): Promise<unknown> {
    return http.post(endpoints.budgets.acknowledge(String(id), String(alertId)))
  },
}