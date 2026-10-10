import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { CATEGORY_TYPES, CATEGORY_TYPE_LABELS } from '../labels'
import {
  categoryFormSchema,
  EMPTY_CATEGORY_FORM,
  toCreateCategoryInput,
  toUpdateCategoryInput,
  type CategoryFormValues,
} from '../schemas'
import { useCreateCategory, useUpdateCategory } from '../hooks'
import type { Category } from '../types'

export interface CategoryFormModalProps {
  category: Category | null
  open: boolean
  onClose: () => void
}

export function CategoryFormModal({ category, open, onClose }: CategoryFormModalProps) {
  const creating = category === null
  const create = useCreateCategory()
  const update = useUpdateCategory()
  const toast = useToast()

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<CategoryFormValues>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: EMPTY_CATEGORY_FORM,
  })

  /** Backend field names (Create/UpdateCategoryRequest) mapped onto the form. */
  const serverFieldAliases: Partial<Record<string, keyof CategoryFormValues>> = {
    name: 'name',
    type: 'type',
    icon: 'icon',
    color: 'color',
    excludeFromBudgets: 'excludeFromBudgets',
  }

  /** Only map onto inputs the current mode renders, so nothing is lost. */
  const fieldIsVisible = (field: keyof CategoryFormValues): boolean => {
    if (field === 'type') return creating
    return true
  }

  const reportError = (cause: unknown, fallback: string): void => {
    if (cause instanceof ApiError && cause.fieldErrors.length > 0) {
      const unmapped: string[] = []
      for (const fieldError of cause.fieldErrors) {
        const formField = serverFieldAliases[fieldError.field]
        if (formField && fieldIsVisible(formField)) {
          setError(formField, { type: 'server', message: fieldError.message })
        } else {
          unmapped.push(fieldError.message)
        }
      }
      if (unmapped.length > 0) {
        toast.push({ tone: 'error', message: unmapped.join('; ') })
      }
      return
    }
    toast.push({
      tone: 'error',
      message: cause instanceof ApiError ? cause.message : fallback,
    })
  }

  useEffect(() => {
    if (!open) return
    if (creating) {
      reset(EMPTY_CATEGORY_FORM)
    } else {
      reset({
        ...EMPTY_CATEGORY_FORM,
        type: category.type,
        name: category.name,
        icon: category.icon ?? '',
        color: category.color ?? '',
        excludeFromBudgets: category.excludeFromBudgets,
      })
    }
  }, [open, creating, category, reset])

  const onSubmit = handleSubmit((values) => {
    if (creating) {
      create.mutate(toCreateCategoryInput(values), {
        onSuccess: () => {
          toast.push({ tone: 'success', message: 'Category created' })
          onClose()
        },
        onError: (cause) => reportError(cause, 'Could not create the category'),
      })
    } else {
      update.mutate(
        { id: category.id, input: toUpdateCategoryInput(values) },
        {
          onSuccess: () => {
            toast.push({ tone: 'success', message: 'Category updated' })
            onClose()
          },
          onError: (cause) => reportError(cause, 'Could not update the category'),
        },
      )
    }
  })

  return (
    <Modal
      open={open}
      title={creating ? 'New category' : 'Edit category'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="category-form" loading={create.isPending || update.isPending}>
            {creating ? 'Create category' : 'Save changes'}
          </Button>
        </>
      }
    >
      <form id="category-form" onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <Input
          label="Name"
          placeholder="Food & Groceries"
          error={errors.name?.message}
          autoComplete="off"
          {...register('name')}
        />

        {creating && (
          <Select label="Type" error={errors.type?.message} {...register('type')}>
            {CATEGORY_TYPES.map((type) => (
              <option key={type} value={type}>
                {CATEGORY_TYPE_LABELS[type]}
              </option>
            ))}
          </Select>
        )}

        {!creating && (
          <dl className="grid gap-3 rounded-md border border-border bg-surface-sunken p-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs text-muted">Type</dt>
              <dd className="mt-0.5 text-text">{CATEGORY_TYPE_LABELS[category.type]}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted">Sort order</dt>
              <dd className="mt-0.5 text-text">{category.sortOrder}</dd>
            </div>
          </dl>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Icon"
            placeholder="utensils"
            hint="Icon name from the default set"
            error={errors.icon?.message}
            autoComplete="off"
            {...register('icon')}
          />
          <Input
            label="Colour"
            placeholder="#16a34a"
            hint="Optional accent for this category"
            error={errors.color?.message}
            autoComplete="off"
            {...register('color')}
          />
        </div>

        <label className="flex items-center gap-2 text-sm font-medium text-text">
          <input
            type="checkbox"
            className="h-4 w-4"
            {...register('excludeFromBudgets')}
          />
          Exclude transactions in this category from budget calculations
        </label>
      </form>
    </Modal>
  )
}