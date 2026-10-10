import { http, type QueryValue } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  CreateRecurringInput,
  CreateRecurringResponse,
  OccurrencePreview,
  RecurringFilter,
  RecurringPage,
  RecurringRun,
  RecurringRunPage,
  RecurringTransaction,
  UpdateRecurringInput,
} from './types'

/** Serialises the recurring rules filter into the backend query contract (E.9). */
export function buildRecurringQuery(filter: RecurringFilter): Record<string, QueryValue> {
  const query: Record<string, QueryValue> = {
    status: filter.status,
    type: filter.type,
    accountId: filter.accountId,
    categoryId: filter.categoryId,
    dueWithinDays: filter.dueWithinDays,
  }
  if (filter.page !== undefined) query.page = filter.page
  if (filter.size !== undefined) query.size = filter.size
  return query
}

export const recurringApi = {
  list(filter: RecurringFilter): Promise<RecurringPage> {
    return http.get<RecurringPage>(endpoints.recurring.list, {
      query: { ...buildRecurringQuery(filter), page: filter.page ?? 0, size: filter.size ?? 50 },
    })
  },

  create(input: CreateRecurringInput): Promise<CreateRecurringResponse> {
    return http.post<CreateRecurringResponse>(endpoints.recurring.create, input)
  },

  upcoming(days?: number): Promise<RecurringTransaction[]> {
    return http.get<RecurringTransaction[]>(endpoints.recurring.upcoming, {
      query: { days },
    })
  },

  detail(id: number): Promise<RecurringTransaction> {
    return http.get<RecurringTransaction>(endpoints.recurring.detail(String(id)))
  },

  update(id: number, input: UpdateRecurringInput): Promise<RecurringTransaction> {
    return http.patch<RecurringTransaction>(endpoints.recurring.update(String(id)), input)
  },

  remove(id: number): Promise<void> {
    return http.delete<void>(endpoints.recurring.remove(String(id)))
  },

  pause(id: number): Promise<RecurringTransaction> {
    return http.post<RecurringTransaction>(endpoints.recurring.pause(String(id)))
  },

  resume(id: number): Promise<RecurringTransaction> {
    return http.post<RecurringTransaction>(endpoints.recurring.resume(String(id)))
  },

  cancel(id: number): Promise<RecurringTransaction> {
    return http.post<RecurringTransaction>(endpoints.recurring.cancel(String(id)))
  },

  runNow(id: number, idempotencyKey: string): Promise<RecurringRun> {
    return http.post<RecurringRun>(endpoints.recurring.runNow(String(id)), undefined, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
  },

  preview(id: number, count = 5): Promise<OccurrencePreview> {
    return http.get<OccurrencePreview>(endpoints.recurring.preview(String(id)), {
      query: { count },
    })
  },

  runs(id: number, page = 0, size = 20): Promise<RecurringRunPage> {
    return http.get<RecurringRunPage>(endpoints.recurring.runs(String(id)), {
      query: { page, size },
    })
  },
}