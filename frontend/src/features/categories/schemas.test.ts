import { describe, expect, it } from 'vitest'
import {
  categoryFormSchema,
  EMPTY_CATEGORY_FORM,
  toCreateCategoryInput,
  toUpdateCategoryInput,
} from './schemas'

describe('categoryFormSchema', () => {
  it('accepts a valid category', () => {
    const parsed = categoryFormSchema.safeParse({
      name: 'Food & Groceries',
      type: 'EXPENSE',
      icon: 'utensils',
      color: '#16a34a',
      excludeFromBudgets: true,
    })
    expect(parsed.success).toBe(true)
  })

  it('requires a name and trims it', () => {
    const missing = categoryFormSchema.safeParse({ ...EMPTY_CATEGORY_FORM, name: '   ' })
    expect(missing.success).toBe(false)
    if (!missing.success) {
      expect(missing.error.issues.some((issue) => issue.path[0] === 'name')).toBe(true)
    }
    const parsed = categoryFormSchema.safeParse({ ...EMPTY_CATEGORY_FORM, name: '  Food  ' })
    expect(parsed.success).toBe(true)
    if (parsed.success) expect(parsed.data.name).toBe('Food')
  })

  it('rejects colours that are not six-digit hex', () => {
    const parsed = categoryFormSchema.safeParse({
      ...EMPTY_CATEGORY_FORM,
      name: 'Food',
      color: 'green',
    })
    expect(parsed.success).toBe(false)
  })

  it('accepts an empty colour', () => {
    const parsed = categoryFormSchema.safeParse({ ...EMPTY_CATEGORY_FORM, name: 'Food', color: '' })
    expect(parsed.success).toBe(true)
  })

  it('allows only INCOME or EXPENSE as the type', () => {
    const parsed = categoryFormSchema.safeParse({ ...EMPTY_CATEGORY_FORM, type: 'SAVINGS' })
    expect(parsed.success).toBe(false)
  })
})

describe('input mapping', () => {
  it('blank icon and colour become null, not empty strings', () => {
    const input = toCreateCategoryInput({
      name: 'Food',
      type: 'EXPENSE',
      icon: '',
      color: '  ',
      excludeFromBudgets: false,
    })
    expect(input.icon).toBeNull()
    expect(input.color).toBeNull()
  })

  it('create keeps the chosen type and exclude flag', () => {
    const input = toCreateCategoryInput({
      name: 'Salary',
      type: 'INCOME',
      icon: 'briefcase',
      color: '#22c55e',
      excludeFromBudgets: true,
    })
    expect(input).toEqual({
      name: 'Salary',
      type: 'INCOME',
      icon: 'briefcase',
      color: '#22c55e',
      excludeFromBudgets: true,
    })
  })

  it('update ignores the form type, which is not editable', () => {
    const input = toUpdateCategoryInput({
      name: 'Salary',
      type: 'INCOME',
      icon: '',
      color: '#22c55e',
      excludeFromBudgets: true,
    })
    expect(input).toEqual({
      name: 'Salary',
      icon: null,
      color: '#22c55e',
      excludeFromBudgets: true,
    })
  })
})