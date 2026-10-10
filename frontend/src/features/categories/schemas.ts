import { z } from 'zod'
import type { CreateCategoryInput, UpdateCategoryInput } from './types'

const COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/

const categoryTypeEnum = z.enum(['INCOME', 'EXPENSE'])

export const categoryFormSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(60, 'Max 60 characters'),
  type: categoryTypeEnum,
  icon: z.string().trim().max(40, 'Max 40 characters'),
  color: z
    .string()
    .trim()
    .refine((value) => value === '' || COLOR_PATTERN.test(value), 'Use hex like #12AB34'),
  excludeFromBudgets: z.boolean(),
})

export type CategoryFormValues = z.infer<typeof categoryFormSchema>

export const EMPTY_CATEGORY_FORM: CategoryFormValues = {
  name: '',
  type: 'EXPENSE',
  icon: '',
  color: '',
  excludeFromBudgets: false,
}

/**
 * Optional wire fields are nullable, not empty strings: the backend validates
 * {@code color} with an anchored {@code @Pattern}, which an empty string fails
 * (null passes). Send null for anything left blank.
 */
function blankToNull(value: string): string | null {
  return value.trim() === '' ? null : value
}

export function toCreateCategoryInput(values: CategoryFormValues): CreateCategoryInput {
  return {
    name: values.name,
    type: values.type,
    icon: blankToNull(values.icon),
    color: blankToNull(values.color),
    excludeFromBudgets: values.excludeFromBudgets,
  }
}

export function toUpdateCategoryInput(values: CategoryFormValues): UpdateCategoryInput {
  return {
    name: values.name,
    icon: blankToNull(values.icon),
    color: blankToNull(values.color),
    excludeFromBudgets: values.excludeFromBudgets,
  }
}