import { useEffect, useState, type ChangeEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useToast } from '@/app/providers/ToastProvider'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { formatBusinessDate } from '@/lib/dates'
import { fractionDigitsFor, formatMinor, formatMoney, parseAmountToMinor } from '@/lib/money'
import { ApiError } from '@/services/http/ApiError'
import { TRANSACTION_STATUSES, TRANSACTION_TYPES } from '@/types/enums'
import type { TransactionStatus, TransactionType } from '@/types/enums'
import { transactionsApi } from '../api'
import { parseFilter, serializeFilter } from '../codec'
import { ReverseTransactionDialog } from '../components/ReverseTransactionDialog'
import { TransactionFormModal } from '../components/TransactionFormModal'
import { TransferFormModal } from '../components/TransferFormModal'
import { useTransactionFilterOptions, useTransactionSummary, useTransactions } from '../hooks'
import {
  TRANSACTION_SORT_OPTIONS,
  TRANSACTION_STATUS_LABELS,
  TRANSACTION_TYPE_LABELS,
  transactionStatusTone,
  transactionTypeTone,
} from '../labels'
import type { Transaction, TransactionFilter } from '../types'

const SEARCH_DEBOUNCE_MS = 300

type FlagKey = 'recurringOnly' | 'importedOnly' | 'hasNotes' | 'hasReversal' | 'baseCurrencyOnly'

const FLAG_LABELS: ReadonlyArray<{ key: FlagKey; label: string }> = [
  { key: 'recurringOnly', label: 'Recurring only' },
  { key: 'importedOnly', label: 'Imported only' },
  { key: 'hasNotes', label: 'Has notes' },
  { key: 'hasReversal', label: 'Has reversal' },
  { key: 'baseCurrencyOnly', label: 'Base currency only' },
]

export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const toast = useToast()

  const [filter, setFilter] = useState<TransactionFilter>(() => parseFilter(searchParams))
  const [qDraft, setQDraft] = useState(filter.q ?? '')
  const [minDraft, setMinDraft] = useState(
    filter.minAmountMinor !== undefined
      ? formatMinor(filter.minAmountMinor, fractionDigitsFor(filter.currencyCode ?? 'USD'))
      : '',
  )
  const [maxDraft, setMaxDraft] = useState(
    filter.maxAmountMinor !== undefined
      ? formatMinor(filter.maxAmountMinor, fractionDigitsFor(filter.currencyCode ?? 'USD'))
      : '',
  )

  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<Transaction | null>(null)
  const [transferring, setTransferring] = useState(false)
  const [reversing, setReversing] = useState<Transaction | null>(null)
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const trimmed = qDraft.trim()
      setFilter((current) =>
        (current.q ?? '') === trimmed ? current : { ...current, q: trimmed || undefined, page: 0 },
      )
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [qDraft])

  const serializedFilter = serializeFilter(filter).toString()
  useEffect(() => {
    if (searchParams.toString() !== serializedFilter) {
      setSearchParams(new URLSearchParams(serializedFilter), { replace: true })
    }
  }, [searchParams, serializedFilter, setSearchParams])

  const patch = (changes: Partial<TransactionFilter>): void =>
    setFilter((current) => ({ ...current, ...changes, page: changes.page ?? 0 }))

  const toggleFlag = (key: FlagKey): void =>
    setFilter((current) => {
      const next: TransactionFilter = { ...current, page: 0 }
      if (current[key] === true) delete next[key]
      else next[key] = true
      return next
    })

  const clearFilters = (): void => {
    setFilter({})
    setQDraft('')
    setMinDraft('')
    setMaxDraft('')
  }

  const transactions = useTransactions(filter)
  const summary = useTransactionSummary(filter)
  const options = useTransactionFilterOptions()

  const baseCurrency = summary.data?.baseCurrencyCode ?? 'USD'
  const amountDigits = fractionDigitsFor(filter.currencyCode ?? baseCurrency)

  const accounts = options.data?.accounts ?? []
  const categories = options.data?.categories ?? []
  const rows = transactions.data?.content ?? []

  const onAmountChange =
    (kind: 'min' | 'max') =>
    (event: ChangeEvent<HTMLInputElement>): void => {
      const text = event.target.value
      if (kind === 'min') setMinDraft(text)
      else setMaxDraft(text)
      const parsed =
        text.trim() === '' ? undefined : (parseAmountToMinor(text, amountDigits) ?? undefined)
      patch(kind === 'min' ? { minAmountMinor: parsed } : { maxAmountMinor: parsed })
    }

  const onExport = async (): Promise<void> => {
    setExporting(true)
    try {
      const { blob, filename } = await transactionsApi.exportCsv(filter)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch {
      toast.push({ tone: 'error', message: 'Could not export the CSV file' })
    } finally {
      setExporting(false)
    }
  }

  const openModal = (transaction: Transaction): void => {
    if (
      transaction.type === 'TRANSFER' ||
      transaction.reversedAt !== null ||
      transaction.reversalOfTransactionId !== null
    ) {
      return
    }
    setSelected(transaction)
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Transactions</h1>
          <p className="mt-1 text-sm text-muted">
            Income, expenses and adjustments across every account.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => void onExport()} loading={exporting}>
            Export CSV
          </Button>
          <Button variant="secondary" onClick={() => setTransferring(true)}>
            New transfer
          </Button>
          <Button onClick={() => setCreating(true)}>New transaction</Button>
        </div>
      </header>

      <section aria-label="Summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Matching rows</p>
            <p className="mt-2 text-2xl font-semibold text-text num">
              {summary.isLoading ? '—' : (summary.data?.count ?? 0)}
            </p>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Income</p>
            <p className="mt-2 text-2xl font-semibold text-success num">
              {summary.isLoading
                ? '—'
                : formatMoney(summary.data?.incomeBaseMinor ?? 0, baseCurrency, undefined, 'en-US')}
            </p>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Expenses</p>
            <p className="mt-2 text-2xl font-semibold text-danger num">
              {summary.isLoading
                ? '—'
                : formatMoney(
                    summary.data?.expenseBaseMinor ?? 0,
                    baseCurrency,
                    undefined,
                    'en-US',
                  )}
            </p>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Net</p>
            <p className="mt-2 text-2xl font-semibold text-text num">
              {summary.isLoading
                ? '—'
                : formatMoney(summary.data?.netBaseMinor ?? 0, baseCurrency, undefined, 'en-US')}
            </p>
            {summary.isError && <p className="mt-1 text-xs text-danger">Could not load totals.</p>}
          </CardBody>
        </Card>
      </section>

      <section aria-label="Filters" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Input
          label="Search"
          placeholder="Description, merchant or notes"
          value={qDraft}
          onChange={(event) => setQDraft(event.target.value)}
        />
        <Select
          label="Account"
          value={filter.accountIds?.[0] !== undefined ? String(filter.accountIds[0]) : ''}
          onChange={(event) => {
            const value = event.target.value
            patch({ accountIds: value === '' ? undefined : [Number(value)] })
          }}
        >
          <option value="">All accounts</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
              {account.status === 'ARCHIVED' ? ' (archived)' : ''}
            </option>
          ))}
        </Select>
        <Select
          label="Category"
          value={filter.categoryIds?.[0] !== undefined ? String(filter.categoryIds[0]) : ''}
          onChange={(event) => {
            const value = event.target.value
            patch({ categoryIds: value === '' ? undefined : [Number(value)] })
          }}
        >
          <option value="">All categories</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
        <Select
          label="Type"
          value={filter.types?.[0] ?? ''}
          onChange={(event) => {
            const value = event.target.value
            patch({ types: value === '' ? undefined : [value as TransactionType] })
          }}
        >
          <option value="">All types</option>
          {TRANSACTION_TYPES.map((type) => (
            <option key={type} value={type}>
              {TRANSACTION_TYPE_LABELS[type]}
            </option>
          ))}
        </Select>
        <Select
          label="Status"
          value={filter.statuses?.[0] ?? ''}
          onChange={(event) => {
            const value = event.target.value
            patch({
              statuses: value === '' ? undefined : [value as TransactionStatus],
            })
          }}
        >
          <option value="">All statuses</option>
          {TRANSACTION_STATUSES.map((status) => (
            <option key={status} value={status}>
              {TRANSACTION_STATUS_LABELS[status]}
            </option>
          ))}
        </Select>
        <Input
          label="From"
          type="date"
          value={filter.from ?? ''}
          onChange={(event) => patch({ from: event.target.value || undefined })}
        />
        <Input
          label="To"
          type="date"
          value={filter.to ?? ''}
          onChange={(event) => patch({ to: event.target.value || undefined })}
        />
        <Input
          label="Min amount"
          placeholder="0.00"
          numeric
          inputMode="decimal"
          value={minDraft}
          onChange={onAmountChange('min')}
        />
        <Input
          label="Max amount"
          placeholder="0.00"
          numeric
          inputMode="decimal"
          value={maxDraft}
          onChange={onAmountChange('max')}
        />
        <Select
          label="Currency"
          value={filter.currencyCode ?? ''}
          onChange={(event) => patch({ currencyCode: event.target.value || undefined })}
        >
          <option value="">All currencies</option>
          {(options.data?.currencies ?? []).map((code) => (
            <option key={code} value={code}>
              {code}
            </option>
          ))}
        </Select>
        <Select
          label="Sort"
          value={filter.sort ?? ''}
          onChange={(event) => patch({ sort: event.target.value || undefined })}
        >
          {TRANSACTION_SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
        <div className="flex items-end">
          <Button variant="secondary" onClick={clearFilters}>
            Clear filters
          </Button>
        </div>
      </section>

      <details className="rounded-lg border border-border">
        <summary className="cursor-pointer px-4 py-2 text-sm font-medium text-text">
          More filters
        </summary>
        <div className="grid gap-3 px-4 pb-4 sm:grid-cols-2 lg:grid-cols-5">
          {FLAG_LABELS.map(({ key, label }) => (
            <label key={key} className="flex items-center gap-2 text-sm font-medium text-text">
              <input
                type="checkbox"
                className="h-4 w-4"
                checked={filter[key] === true}
                onChange={() => toggleFlag(key)}
              />
              {label}
            </label>
          ))}
        </div>
      </details>

      <section aria-label="Transactions" className="flex flex-col gap-3">
        {transactions.isLoading && (
          <p className="flex items-center gap-2 text-sm text-muted">
            <LoadingSpinner label="Loading transactions" /> Loading transactions…
          </p>
        )}
        {transactions.isError && (
          <InlineError
            message={
              transactions.error instanceof ApiError
                ? transactions.error.message
                : 'Could not load transactions.'
            }
            onRetry={() => void transactions.refetch()}
          />
        )}
        {transactions.data && rows.length === 0 && (
          <Card>
            <CardBody>
              <p className="text-sm text-muted">No transactions match these filters yet.</p>
            </CardBody>
          </Card>
        )}
        {rows.length > 0 && (
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-sm">
              <thead className="bg-surface-sunken text-xs uppercase tracking-wide text-subtle">
                <tr>
                  <th className="px-4 py-3 text-left">Date</th>
                  <th className="px-4 py-3 text-left">Description</th>
                  <th className="px-4 py-3 text-left">Account</th>
                  <th className="px-4 py-3 text-left">Category</th>
                  <th className="px-4 py-3 text-left">Type</th>
                  <th className="px-4 py-3 text-right">Amount</th>
                  <th className="px-4 py-3 text-left">Status</th>
                  <th className="px-4 py-3 text-right">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const editable =
                    row.type !== 'TRANSFER' &&
                    row.reversedAt === null &&
                    row.reversalOfTransactionId === null
                  const reversible =
                    row.reversedAt === null &&
                    row.reversalOfTransactionId === null &&
                    row.status !== 'VOID'
                  return (
                    <tr
                      key={row.id}
                      onClick={() => openModal(row)}
                      className={
                        editable
                          ? 'cursor-pointer border-t border-border hover:bg-surface-sunken/60'
                          : 'border-t border-border'
                      }
                    >
                      <td className="whitespace-nowrap px-4 py-3 text-text">
                        {formatBusinessDate(row.occurredOn)}
                      </td>
                      <td className="max-w-56 px-4 py-3">
                        <p className="truncate font-medium text-text">{row.description || '—'}</p>
                        {row.merchant && (
                          <p className="truncate text-xs text-muted">{row.merchant}</p>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-text">{row.account.name}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-text">
                        {row.category ? row.category.name : '—'}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        <Badge tone={transactionTypeTone(row.type)}>
                          {TRANSACTION_TYPE_LABELS[row.type]}
                        </Badge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right num">
                        <span
                          className={row.direction === 'INFLOW' ? 'text-success' : 'text-danger'}
                        >
                          {row.direction === 'INFLOW' ? '+' : '−'}
                          {formatMoney(row.amountMinor, row.currencyCode, undefined, 'en-US')}
                        </span>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3">
                        {row.status === 'POSTED' ? (
                          <span className="text-xs text-muted">
                            {TRANSACTION_STATUS_LABELS.POSTED}
                          </span>
                        ) : (
                          <Badge tone={transactionStatusTone(row.status)}>
                            {TRANSACTION_STATUS_LABELS[row.status]}
                          </Badge>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right">
                        {editable && (
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={`Edit ${row.description ?? `transaction ${row.id}`}`}
                            onClick={(event) => {
                              event.stopPropagation()
                              openModal(row)
                            }}
                          >
                            Edit
                          </Button>
                        )}
                        {reversible && (
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={`Reverse ${row.description ?? `transaction ${row.id}`}`}
                            onClick={(event) => {
                              event.stopPropagation()
                              setReversing(row)
                            }}
                          >
                            Reverse
                          </Button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {transactions.data && transactions.data.totalPages > 1 && (
          <div className="flex items-center justify-between">
            <Button
              variant="secondary"
              size="sm"
              disabled={transactions.data.first}
              onClick={() => patch({ page: (filter.page ?? 0) - 1 })}
            >
              Previous
            </Button>
            <p className="text-xs text-muted">
              Page {transactions.data.page + 1} of {transactions.data.totalPages}
            </p>
            <Button
              variant="secondary"
              size="sm"
              disabled={transactions.data.last}
              onClick={() => patch({ page: (filter.page ?? 0) + 1 })}
            >
              Next
            </Button>
          </div>
        )}
      </section>

      <TransactionFormModal
        transaction={selected}
        open={creating || selected !== null}
        onClose={() => {
          setCreating(false)
          setSelected(null)
        }}
      />

      <TransferFormModal open={transferring} onClose={() => setTransferring(false)} />

      <ReverseTransactionDialog
        transaction={reversing}
        open={reversing !== null}
        onClose={() => setReversing(null)}
      />
    </div>
  )
}
