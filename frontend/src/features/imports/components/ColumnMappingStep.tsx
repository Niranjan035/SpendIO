import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { useTransactionFilterOptions } from '@/features/transactions/hooks'
import { IMPORT_MAPPING_FIELDS } from '../labels'
import { useImportColumns, useSaveImportMapping, useValidateImport } from '../hooks'
import {
  EMPTY_MAPPING_OPTIONS,
  mappingAssignmentsSchema,
  type MappingOptionsValues,
} from '../schemas'
import type { SaveMappingInput } from '../types'

const NO_FIELD = ''

export function ColumnMappingStep({ importId }: { importId: number }) {
  const toast = useToast()
  const columnsQuery = useImportColumns(importId)
  const save = useSaveImportMapping()
  const validate = useValidateImport()
  const optionSets = useTransactionFilterOptions()

  const [assignments, setAssignments] = useState<Record<string, string>>({})
  const [options, setOptions] = useState<MappingOptionsValues>(EMPTY_MAPPING_OPTIONS)
  const [formError, setFormError] = useState<string | null>(null)
  const seededRef = useRef(false)

  const columns = columnsQuery.data

  useEffect(() => {
    seededRef.current = false
  }, [importId])

  useEffect(() => {
    if (seededRef.current || !columns) return
    const seeded: Record<string, string> = {}
    for (const pointer of columns.mapping) {
      if (pointer.column) seeded[pointer.field] = pointer.column
    }
    setAssignments(seeded)
    seededRef.current = true
  }, [columns])

  const fieldOfColumn = useMemo(() => {
    const index: Record<string, string> = {}
    for (const [field, column] of Object.entries(assignments)) index[column] = field
    return index
  }, [assignments])

  const setColumnField = (column: string, field: string): void => {
    setAssignments((current) => {
      const next = { ...current }
      const previousField = fieldOfColumn[column]
      if (previousField && (field === NO_FIELD || next[previousField] === column)) {
        delete next[previousField]
      }
      if (field !== NO_FIELD) next[field] = column
      return next
    })
    setFormError(null)
  }

  const setOption = <K extends keyof MappingOptionsValues>(
    key: K,
    value: MappingOptionsValues[K],
  ): void => setOptions((current) => ({ ...current, [key]: value }))

  const onSave = async (): Promise<void> => {
    const parsed = mappingAssignmentsSchema.safeParse(assignments)
    if (!parsed.success) {
      setFormError(parsed.error.issues[0]?.message ?? 'Finish the mapping first')
      return
    }
    const input: SaveMappingInput = {
      columnMappings: Object.fromEntries(
        Object.entries(assignments).map(([field, column]) => [column, { field }]),
      ),
      dateColumn: assignments.date,
      amountColumn: assignments.amount,
      descriptionColumn: assignments.description,
      debitColumn: assignments.debit,
      creditColumn: assignments.credit,
      categoryColumn: assignments.category,
      accountColumn: assignments.account,
      currencyColumn: assignments.currency,
      typeColumn: assignments.type,
      dateFormat: options.dateFormat || undefined,
      options: {
        defaultCurrencyCode: options.defaultCurrencyCode || undefined,
        defaultDateFormat: options.defaultDateFormat || undefined,
        targetAccountId: options.targetAccountId ? Number(options.targetAccountId) : undefined,
        createMissingCategories: options.createMissingCategories,
        skipDuplicates: options.skipDuplicates,
        allowPartialImport: options.allowPartialImport,
        defaultType: options.defaultType || undefined,
      },
    }
    try {
      await save.mutateAsync({ id: importId, input })
      await validate.mutateAsync(importId)
      toast.push({ tone: 'success', message: 'Mapping saved — rows validated' })
    } catch (cause) {
      toast.push({
        tone: 'error',
        message:
          cause instanceof ApiError ? cause.message : 'Could not save the column mapping',
      })
    }
  }

  if (columnsQuery.isLoading) return <LoadingSpinner label="Loading columns" />
  if (columnsQuery.isError) {
    return (
      <InlineError
        message={
          columnsQuery.error instanceof ApiError
            ? columnsQuery.error.message
            : 'Could not load the CSV columns'
        }
        onRetry={() => void columnsQuery.refetch()}
      />
    )
  }
  if (!columns) return null

  const accounts = (optionSets.data?.accounts ?? []).filter(
    (account) => account.status === 'ACTIVE',
  )
  const currencies = optionSets.data?.currencies ?? []
  const saving = save.isPending || validate.isPending

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs uppercase text-subtle">
              <th scope="col" className="py-2 pr-4 font-medium">
                CSV column
              </th>
              <th scope="col" className="py-2 pr-4 font-medium">
                Sample values
              </th>
              <th scope="col" className="py-2 font-medium">
                Maps to
              </th>
            </tr>
          </thead>
          <tbody>
            {columns.columns.map((column) => (
              <tr key={column.headerName} className="border-b border-border/60 align-top">
                <td className="py-3 pr-4 font-medium text-text">{column.headerName}</td>
                <td className="py-3 pr-4 text-xs text-muted">
                  {column.sampleValues.slice(0, 3).join(', ') || '—'}
                </td>
                <td className="py-3">
                  <Select
                    aria-label={`Field for ${column.headerName}`}
                    value={fieldOfColumn[column.headerName] ?? NO_FIELD}
                    onChange={(event) => setColumnField(column.headerName, event.target.value)}
                  >
                    <option value={NO_FIELD}>— not used —</option>
                    {IMPORT_MAPPING_FIELDS.map((field) => (
                      <option key={field.value} value={field.value}>
                        {field.label}
                      </option>
                    ))}
                  </Select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Select
          label="Default currency"
          hint="When the file has no currency column"
          value={options.defaultCurrencyCode}
          onChange={(event) => setOption('defaultCurrencyCode', event.target.value)}
        >
          <option value="">Auto</option>
          {currencies.map((currency) => (
            <option key={currency} value={currency}>
              {currency}
            </option>
          ))}
        </Select>
        <Select
          label="Target account"
          hint="When the file has no account column"
          value={options.targetAccountId}
          onChange={(event) => setOption('targetAccountId', event.target.value)}
        >
          <option value="">Match per row</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>
        <Select
          label="Date format"
          value={options.dateFormat}
          onChange={(event) => setOption('dateFormat', event.target.value)}
        >
          <option value="">Auto-detect</option>
          <option value="yyyy-MM-dd">2026-03-09 (yyyy-MM-dd)</option>
          <option value="dd/MM/yyyy">09/03/2026 (dd/MM/yyyy)</option>
          <option value="MM/dd/yyyy">03/09/2026 (MM/dd/yyyy)</option>
        </Select>
      </div>

      <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-text">
        {(
          [
            ['skipDuplicates', 'Skip duplicates'],
            ['allowPartialImport', 'Allow partial import'],
            ['createMissingCategories', 'Create missing categories'],
          ] as const
        ).map(([key, label]) => (
          <label key={key} className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={options[key]}
              onChange={(event) => setOption(key, event.target.checked)}
            />
            {label}
          </label>
        ))}
      </div>

      {formError && (
        <p role="alert" className="text-sm text-danger">
          {formError}
        </p>
      )}

      <div className="flex justify-end">
        <Button onClick={() => void onSave()} loading={saving}>
          Save mapping and validate
        </Button>
      </div>
    </div>
  )
}
