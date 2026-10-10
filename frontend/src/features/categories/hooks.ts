import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { transactionsKeys } from '@/features/transactions/hooks'
import { categoriesApi } from './api'
import type { Category, CategoryFilter, CreateCategoryInput, UpdateCategoryInput } from './types'

export const categoryKeys = {
  all: ['categories'] as const,
  list: (filter: CategoryFilter) => ['categories', 'list', filter] as const,
  detail: (id: number) => ['categories', 'detail', id] as const,
}

function invalidateCategories(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: categoryKeys.all })
  // The transaction form and filter sidebar read categories from the transactions
  // filter-options cache, so category CRUD must refresh it too (or the New
  // Transaction category dropdown shows a stale, empty list).
  void client.invalidateQueries({ queryKey: transactionsKeys.options })
}

export function useCategories(filter: CategoryFilter) {
  return useQuery({
    queryKey: categoryKeys.list(filter),
    queryFn: () => categoriesApi.list(filter),
  })
}

export function useCreateCategory() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateCategoryInput) => categoriesApi.create(input),
    onSuccess: () => invalidateCategories(client),
  })
}

export function useUpdateCategory() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: UpdateCategoryInput }) =>
      categoriesApi.update(id, input),
    onSuccess: (category) => {
      invalidateCategories(client)
      client.setQueryData<Category>(categoryKeys.detail(category.id), category)
    },
  })
}

export function useDeleteCategory() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => categoriesApi.remove(id),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: categoryKeys.detail(id) })
      invalidateCategories(client)
    },
  })
}

export function useArchiveCategory() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => categoriesApi.archive(id),
    onSuccess: (category) => {
      invalidateCategories(client)
      client.setQueryData<Category>(categoryKeys.detail(category.id), category)
    },
  })
}

export function useRestoreCategory() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => categoriesApi.restore(id),
    onSuccess: (category) => {
      invalidateCategories(client)
      client.setQueryData<Category>(categoryKeys.detail(category.id), category)
    },
  })
}