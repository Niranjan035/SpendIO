import { http } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  Category,
  CategoryFilter,
  CategoryPage,
  CreateCategoryInput,
  UpdateCategoryInput,
} from './types'

export function buildCategoryQuery(
  filter: CategoryFilter,
): Record<string, string | number | boolean> {
  return {
    type: filter.type ?? '',
    status: filter.status ?? '',
    q: filter.q ?? '',
    page: filter.page ?? 0,
    size: filter.size ?? 50,
    sort: filter.sort ?? '',
  }
}

export const categoriesApi = {
  list(filter: CategoryFilter): Promise<CategoryPage> {
    return http.get<CategoryPage>(endpoints.categories.list, { query: buildCategoryQuery(filter) })
  },

  create(input: CreateCategoryInput): Promise<Category> {
    return http.post<Category>(endpoints.categories.create, input)
  },

  detail(id: number): Promise<Category> {
    return http.get<Category>(endpoints.categories.detail(String(id)))
  },

  update(id: number, input: UpdateCategoryInput): Promise<Category> {
    return http.patch<Category>(endpoints.categories.update(String(id)), input)
  },

  remove(id: number, reassignTo?: number): Promise<void> {
    return http.delete<void>(endpoints.categories.remove(String(id)), {
      query: reassignTo === undefined ? {} : { reassignTo },
    })
  },

  archive(id: number): Promise<Category> {
    return http.post<Category>(endpoints.categories.archive(String(id)))
  },

  restore(id: number): Promise<Category> {
    return http.post<Category>(endpoints.categories.restore(String(id)))
  },
}