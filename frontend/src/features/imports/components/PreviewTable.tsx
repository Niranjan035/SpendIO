import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { formatMoney, fractionDigitsFor, parseAmountToMinor, formatMinor } from '@/lib/money'
import { formatBusinessDate } from '@/lib/dates'
import { useTransactionFilterOptions } from '@/features/transactions/hooks'
import { TRANSACTION_TYPE_LABELS } from '@/features/transactions/labels'
import { IMPORT_ROW_STATUS_LABELS, importRowStatusTone } from '../labels'
import { useApplyRowDecisions, useImportPreview, useUpdateImportRow } from '../hooks'
import { editRowSchema, type EditRowValues } from '../schemas'
import { IMPORT_ROW_STATUSES, type ImportRowStatus } from '@/types/enums'
import type { ImportRowPreview, RowDecisionInput } from '../types'
import { RowErrorList } from './RowErrorList'

const DECIDABLE: readonly ImportRowStatus[] = ['VALID', 'DUPLICATE']
const PAGE_SIZE = 50

function selectable(row: ImportRowPreview): boolean {
  return DECIDABLE.includes(row.status)
}

export function PreviewTable({ importId, readOnly }: { importId: number; readOnly: boolean }) {
  const toast = useToast()
  const [status, setStatus] = useState<ImportRowStatus | ''>('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<ReadonlySet<number>>(new Set())
  const [editing, setEditing] = useState<ImportRowPreview | null>(null)

  const preview = useImportPreview(importId, { status: status || undefined, page, size: PAGE_SIZE })
  const decide = useApplyRowDecisions()

  const rows = preview.data?.content ?? []

  const failureMessage = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const changeFilter = (next: ImportRowStatus | ''): void => {
    setStatus(next)
    setPage(0)
    setSelected(new Set())
  }

  const toggle = (row: ImportRowPreview): void => {
    if (!selectable(row)) return
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(row.id)) next.delete(row.id)
      else next.add(row.id)
      return next
    })
  }

  const applyDecision = (decision: RowDecisionInput['decision']): void => {
    if (selected.size === 0) return
    decide.mutate(
      { id: importId, input: { rowIds: [...selected], decision } },
      {
        onSuccess: () => {
          setSelected(new Set())
          toast.push({ tone: 'success', message: `Rows updated (${decision.toLowerCase()})` })
        },
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: failureMessage(cause, 'Could not update the rows'),
          }),
      },
    )
  }

  if (preview.isLoading) return <LoadingSpinner label="Loading rows" />
  if (preview.isError) {
    return (
      <InlineError
        message={
          preview.error instanceof ApiError ? preview.error.message : 'Could not load the rows'
        }
        onRetry={() => void preview.refetch()}
      />
    )
  }

  const data = preview.data
  const selectableRows = rows.filter(selectable)

  return (
    <Card>
      <CardHeader
        title="Row preview"
        description={
          data ? `${data.totalElements} rows match this filter` : 'Parsed rows from the CSV file'
        }
      />
      <CardBody className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <Select
            label="Status"
            className="w-48"
            value={status}
            onChange={(event) => changeFilter(event.target.value as ImportRowStatus | '')}
          >
            <option value="">All rows</option>
            {IMPORT_ROW_STATUSES.map((rowStatus) => (
              <option key={rowStatus} value={rowStatus}>
                {IMPORT_ROW_STATUS_LABELS[rowStatus]}
              </option>
            ))}
          </Select>

          {!readOnly && (
            <div className="ml-auto flex flex-wrap items-center gap-2">
              <span className="text-xs text-muted" aria-live="polite">
                {selected.size} selected
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={selected.size === 0}
                loading={decide.isPending}
                onClick={() => applyDecision('CONFIRM')}
              >
                Confirm
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={selected.size === 0}
                loading={decide.isPending}
                onClick={() => applyDecision('SKIP')}
              >
                Skip
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={selected.size === 0}
                loading={decide.isPending}
                onClick={() => applyDecision('UNCONFIRM')}
              >
                Unconfirm
              </Button>
            </div>
          )}
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase text-subtle">
                {!readOnly && (
                  <th scope="col" className="w-10 py-2">
                    <span className="sr-only">Select</span>
                  </th>
                )}
                <th scope="col" className="py-2 pr-4 font-medium">
                  Row
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Date
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Description
                </th>
                <th scope="col" className="py-2 pr-4 text-right font-medium">
                  Amount
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Type
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Status
                </th>
                <th scope="col" className="py-2 font-medium">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr>
                  <td colSpan={readOnly ? 7 : 8} className="py-6 text-center text-muted">
                    No rows with this status.
                  </td>
                </tr>
              )}
              {rows.map((row) => (
                <Row
                  key={row.id}
                  row={row}
                  readOnly={readOnly}
                  selected={selected.has(row.id)}
                  onToggle={() => toggle(row)}
                  onEdit={() => setEditing(row)}
                />
              ))}
            </tbody>
          </table>
        </div>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted">
              Page {data.page + 1} of {data.totalPages}
            </p>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={data.first}
                onClick={() => {
                  setPage((current) => Math.max(0, current - 1))
                  setSelected(new Set())
                }}
              >
                Previous
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={data.last}
                onClick={() => {
                  setPage((current) => current + 1)
                  setSelected(new Set())
                }}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </CardBody>

      {editing && (
        <RowEditModal
          key={editing.id}
          importId={importId}
          row={editing}
          onClose={() => setEditing(null)}
        />
      )}
      {!readOnly && selectableRows.length === 0 && rows.length > 0 && (
        <p className="px-4 pb-4 text-xs text-subtle">
          Bulk decisions apply to valid and duplicate rows.
        </p>
      )}
    </Card>
  )
}

function Row({
  row,
  readOnly,
  selected,
  onToggle,
  onEdit,
}: {
  row: ImportRowPreview
  readOnly: boolean
  selected: boolean
  onToggle: () => void
  onEdit: () => void
}) {
  const decidable = selectable(row)
  return (
    <>
      <tr className="border-b border-border/60 align-top">
        {!readOnly && (
          <td className="py-3">
            <input
              type="checkbox"
              aria-label={`Select row ${row.rowNumber}`}
              disabled={!decidable}
              checked={selected}
              onChange={onToggle}
              className="h-4 w-4 accent-primary"
            />
          </td>
        )}
        <td className="py-3 pr-4 text-muted">{row.rowNumber}</td>
        <td className="py-3 pr-4 text-text">
          {row.occurredOn ? formatBusinessDate(row.occurredOn) : '—'}
        </td>
        <td className="max-w-xs truncate py-3 pr-4 text-text">{row.description ?? '—'}</td>
        <td className="py-3 pr-4 text-right text-text">
          {formatMoney(row.amountMinor, row.currencyCode ?? 'USD')}
        </td>
        <td className="py-3 pr-4 text-muted">
          {row.type ? TRANSACTION_TYPE_LABELS[row.type as keyof typeof TRANSACTION_TYPE_LABELS] ?? row.type : '—'}
        </td>
        <td className="py-3 pr-4">
          <Badge tone={importRowStatusTone(row.status)}>
            {IMPORT_ROW_STATUS_LABELS[row.status]}
          </Badge>
        </td>
        <td className="py-3 text-right">
          {!readOnly && (
            <Button variant="ghost" size="sm" onClick={onEdit}>
              Edit
            </Button>
          )}
        </td>
      </tr>
      {row.errors.length > 0 && (
        <tr className="border-b border-border/60">
          <td colSpan={readOnly ? 7 : 8} className="pb-3 pl-6">
            <RowErrorList errors={row.errors} />
          </td>
        </tr>
      )}
    </>
  )
}

function RowEditModal({
  importId,
  row,
  onClose,
}: {
  importId: number
  row: ImportRowPreview
  onClose: () => void
}) {
  const toast = useToast()
  const update = useUpdateImportRow()
  const optionSets = useTransactionFilterOptions()
  const currency = row.currencyCode ?? 'USD'
  const digits = fractionDigitsFor(currency)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<EditRowValues>({
    resolver: zodResolver(editRowSchema),
    defaultValues: {
      occurredOn: row.occurredOn ?? '',
      amount: formatMinor(row.amountMinor, digits),
      description: row.description ?? '',
      type: row.type && row.type !== 'TRANSFER' ? row.type : 'EXPENSE',
      accountId: row.accountId ? String(row.accountId) : '',
      categoryId: row.categoryId ? String(row.categoryId) : '',
    },
  })

  const onSubmit = handleSubmit((values) => {
    const minor = parseAmountToMinor(values.amount, digits)
    if (minor === null) {
      setError('amount', { message: 'Enter a valid amount' })
      return
    }
    update.mutate(
      {
        id: importId,
        rowId: row.id,
        input: {
          occurredOn: values.occurredOn,
          amountMinor: Math.abs(minor),
          description: values.description,
          type: values.type,
          accountId: values.accountId ? Number(values.accountId) : undefined,
          categoryId: values.categoryId ? Number(values.categoryId) : undefined,
        },
      },
      {
        onSuccess: () => {
          toast.push({ tone: 'success', message: `Row ${row.rowNumber} updated` })
          onClose()
        },
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: cause instanceof ApiError ? cause.message : 'Could not update the row',
          }),
      },
    )
  })

  const accounts = (optionSets.data?.accounts ?? []).filter(
    (account) => account.status === 'ACTIVE',
  )
  const categories = optionSets.data?.categories ?? []

  return (
    <Modal
      open
      title={`Edit row ${row.rowNumber}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="import-row-edit-form" loading={update.isPending}>
            Save row
          </Button>
        </>
      }
    >
      <form
        id="import-row-edit-form"
        onSubmit={onSubmit}
        noValidate
        className="flex flex-col gap-4"
      >
        <Input label="Date" type="date" error={errors.occurredOn?.message} {...register('occurredOn')} />
        <Input
          label={`Amount (${currency})`}
          inputMode="decimal"
          error={errors.amount?.message}
          {...register('amount')}
        />
        <Input label="Description" error={errors.description?.message} {...register('description')} />
        <Select label="Type" error={errors.type?.message} {...register('type')}>
          <option value="EXPENSE">Expense</option>
          <option value="INCOME">Income</option>
        </Select>
        <Select label="Account" error={errors.accountId?.message} {...register('accountId')}>
          <option value="">Unmatched</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>
        <Select label="Category" error={errors.categoryId?.message} {...register('categoryId')}>
          <option value="">Unmatched</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
      </form>
    </Modal>
  )
}
