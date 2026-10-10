import type { LucideIcon } from 'lucide-react'
import {
  AlertCircle,
  Book,
  Briefcase,
  Car,
  Code,
  DollarSign,
  FileText,
  Film,
  Gift,
  Heart,
  MoreHorizontal,
  Plane,
  Receipt,
  RefreshCcw,
  ShoppingBag,
  Tag,
  TrendingUp,
  User,
  Utensils,
} from 'lucide-react'
import type { BadgeTone } from '@/components/ui/Badge'
import type { CategoryType } from '@/types/enums'
import type { CategoryStatus } from './types'

export const CATEGORY_TYPES: readonly CategoryType[] = ['INCOME', 'EXPENSE']

export const CATEGORY_TYPE_LABELS: Record<CategoryType, string> = {
  INCOME: 'Income',
  EXPENSE: 'Expense',
}

export const CATEGORY_STATUS_LABELS: Record<CategoryStatus, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
}

/** Icon names used by the backend default category catalog, plus a fallback. */
export const CATEGORY_ICON_MAP: Record<string, LucideIcon> = {
  utensils: Utensils,
  car: Car,
  receipt: Receipt,
  film: Film,
  heart: Heart,
  'shopping-bag': ShoppingBag,
  user: User,
  'refresh-cw': RefreshCcw,
  book: Book,
  plane: Plane,
  gift: Gift,
  'alert-circle': AlertCircle,
  'file-text': FileText,
  'more-horizontal': MoreHorizontal,
  briefcase: Briefcase,
  code: Code,
  'trending-up': TrendingUp,
  'dollar-sign': DollarSign,
}

export const CATEGORY_FALLBACK_ICON: LucideIcon = Tag

export function categoryTypeColor(type: CategoryType): string {
  return type === 'INCOME' ? '#16a34a' : '#dc2626'
}

export function categoryTypeTone(type: CategoryType): BadgeTone {
  return type === 'INCOME' ? 'success' : 'info'
}

export function categoryIcon(name: string | null | undefined): LucideIcon {
  return (name ? CATEGORY_ICON_MAP[name] : undefined) ?? CATEGORY_FALLBACK_ICON
}