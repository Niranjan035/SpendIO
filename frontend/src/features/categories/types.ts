import type { CategoryType } from '@/types/enums'
import type { PageResponse } from '@/types/pagination'

export type CategoryStatus = 'ACTIVE' | 'ARCHIVED'

export interface Category {
  id: number
  userId: number | null
  parentId: number | null
  name: string
  type: CategoryType
  icon: string | null
  color: string | null
  isDefault: boolean
  isSystem: boolean
  sortOrder: number
  excludeFromBudgets: boolean
  status: CategoryStatus
  archivedAt: string | null
  createdAt: string
  updatedAt: string
}

export type CategoryPage = PageResponse<Category>

export interface CategoryFilter {
  type?: CategoryType
  status?: CategoryStatus
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface CreateCategoryInput {
  name: string
  type: CategoryType
  parentId?: number | null
  icon?: string | null
  color?: string | null
  excludeFromBudgets?: boolean
}

export interface UpdateCategoryInput {
  name: string
  icon?: string | null
  color?: string | null
  excludeFromBudgets?: boolean
}