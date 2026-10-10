import { useEffect, useState } from 'react'
import { Card, CardBody } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { LoadingSpinner, InlineError } from '@/components/feedback/LoadingSpinner'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import type { CategoryType } from '@/types/enums'
import type { CategoryStatus } from '../types'
import {
  CATEGORY_ICON_MAP,
  CATEGORY_STATUS_LABELS,
  CATEGORY_TYPES,
  CATEGORY_TYPE_LABELS,
  categoryIcon,
  categoryTypeColor,
  categoryTypeTone,
} from '../labels'
import { useArchiveCategory, useCategories, useDeleteCategory, useRestoreCategory } from '../hooks'
import type { Category, CategoryFilter } from '../types'
import { CategoryFormModal } from '../components/CategoryFormModal'

export function CategoriesPage() {
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState<Category | null>(null)
  const [type, setType] = useState<CategoryType | ''>('')
  const [status, setStatus] = useState<CategoryStatus | ''>('')
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [page, setPage] = useState(0)

  const toast = useToast()
  const archiveCategory = useArchiveCategory()
  const restoreCategory = useRestoreCategory()
  const removeCategory = useDeleteCategory()

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQ(q.trim()), 300)
    return () => clearTimeout(timer)
  }, [q])

  const filter: CategoryFilter = {
    type: type === '' ? undefined : type,
    status: status === '' ? undefined : status,
    q: debouncedQ || undefined,
    page,
    size: 50,
  }

  const categories = useCategories(filter)

  const pushFilter = (patch: Partial<CategoryFilter>): void => {
    setPage(patch.page ?? 0)
    if (patch.type !== undefined) setType(patch.type ?? '')
    if (patch.status !== undefined) setStatus(patch.status ?? '')
  }

  const run = (
    mutate: (
      id: number,
      options: { onSuccess?: () => void; onError?: (cause: unknown) => void },
    ) => void,
    category: Category,
  ): void =>
    mutate(category.id, {
      onSuccess: () =>
        toast.push({
          tone: 'success',
          message: category.status === 'ACTIVE' ? 'Category archived' : 'Category restored',
        }),
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'That did not work',
        }),
    })

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Categories</h1>
          <p className="mt-1 text-sm text-muted">
            Group income and spending so transactions stay easy to understand.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>New category</Button>
      </header>

      <section aria-label="Filters" className="grid gap-4 sm:grid-cols-3">
        <Input
          label="Search"
          placeholder="Category name"
          value={q}
          onChange={(event) => {
            setQ(event.target.value)
            pushFilter({ page: 0 })
          }}
        />
        <Select
          label="Type"
          value={type}
          onChange={(event) => {
            setType(event.target.value as CategoryType | '')
            setPage(0)
          }}
        >
          <option value="">All types</option>
          {CATEGORY_TYPES.map((option) => (
            <option key={option} value={option}>
              {CATEGORY_TYPE_LABELS[option]}
            </option>
          ))}
        </Select>
        <Select
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as CategoryStatus | '')
            setPage(0)
          }}
        >
          <option value="">All</option>
          <option value="ACTIVE">Active</option>
          <option value="ARCHIVED">Archived</option>
        </Select>
      </section>

      <section aria-label="Category list" className="flex flex-col gap-3">
        {categories.isLoading && (
          <p className="flex items-center gap-2 text-sm text-muted">
            <LoadingSpinner label="Loading categories" /> Loading categories…
          </p>
        )}
        {categories.isError && (
          <InlineError
            message={
              categories.error instanceof ApiError
                ? categories.error.message
                : 'Could not load categories.'
            }
            onRetry={() => void categories.refetch()}
          />
        )}
        {categories.data && categories.data.content.length === 0 && (
          <Card>
            <CardBody>
              <p className="text-sm text-muted">No categories match these filters yet.</p>
            </CardBody>
          </Card>
        )}
        {categories.data?.content.map((category) => {
          const Icon = categoryIcon(category.icon)
          const iconFromMap = category.icon ? category.icon in CATEGORY_ICON_MAP : false
          return (
            <Card key={category.id} className="border-border-strong">
              <CardBody className="flex items-center gap-4">
                <span
                  aria-hidden="true"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md"
                  style={{ backgroundColor: `${category.color ?? categoryTypeColor(category.type)}1a` }}
                >
                  <Icon
                    size={18}
                    style={{ color: category.color ?? categoryTypeColor(category.type) }}
                  />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-text">
                    {category.name}
                    {category.isSystem && <Badge tone="primary">System</Badge>}
                    {category.isDefault && <Badge tone="neutral">Default</Badge>}
                    {category.excludeFromBudgets && <Badge tone="warning">Excluded from budgets</Badge>}
                    {category.status === 'ARCHIVED' && <Badge tone="neutral">Archived</Badge>}
                  </p>
                  <p className="mt-0.5 truncate text-xs text-muted">
                    {CATEGORY_TYPE_LABELS[category.type]}
                    {iconFromMap ? '' : category.icon ? ` · icon “${category.icon}”` : ''}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Badge tone={categoryTypeTone(category.type)}>
                    {CATEGORY_TYPE_LABELS[category.type]}
                  </Badge>
                  <span className="text-xs text-subtle">
                    {CATEGORY_STATUS_LABELS[category.status]}
                  </span>
                  <Button variant="ghost" size="sm" onClick={() => setEditing(category)}>
                    Edit
                  </Button>
                  {category.status === 'ACTIVE' ? (
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={archiveCategory.isPending}
                      onClick={() => run(archiveCategory.mutate, category)}
                    >
                      Archive
                    </Button>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={restoreCategory.isPending}
                      onClick={() => run(restoreCategory.mutate, category)}
                    >
                      Restore
                    </Button>
                  )}
                  <Button variant="danger" size="sm" onClick={() => setConfirmingDelete(category)}>
                    Delete
                  </Button>
                </div>
              </CardBody>
            </Card>
          )
        })}
        {categories.data && categories.data.totalPages > 1 && (
          <div className="flex items-center justify-between">
            <Button
              variant="secondary"
              size="sm"
              disabled={categories.data.first}
              onClick={() => pushFilter({ page: page - 1 })}
            >
              Previous
            </Button>
            <p className="text-xs text-muted">
              Page {categories.data.page + 1} of {categories.data.totalPages}
            </p>
            <Button
              variant="secondary"
              size="sm"
              disabled={categories.data.last}
              onClick={() => pushFilter({ page: page + 1 })}
            >
              Next
            </Button>
          </div>
        )}
      </section>

      <CategoryFormModal
        category={editing}
        open={creating || editing !== null}
        onClose={() => {
          setCreating(false)
          setEditing(null)
        }}
      />

      <Modal
        open={confirmingDelete !== null}
        title="Delete category"
        onClose={() => setConfirmingDelete(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmingDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={removeCategory.isPending}
              onClick={() => {
                if (!confirmingDelete) return
                removeCategory.mutate(confirmingDelete.id, {
                  onSuccess: () => {
                    toast.push({ tone: 'success', message: 'Category deleted' })
                    setConfirmingDelete(null)
                  },
                  onError: (cause) =>
                    toast.push({
                      tone: 'error',
                      message:
                        cause instanceof ApiError ? cause.message : 'Could not delete the category',
                    }),
                })
              }}
            >
              Delete category
            </Button>
          </>
        }
      >
        <p className="text-sm text-text">
          This permanently removes “{confirmingDelete?.name ?? ''}”. Categories in use cannot be
          deleted.
        </p>
      </Modal>
    </div>
  )
}