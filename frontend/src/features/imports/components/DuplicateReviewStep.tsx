import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { useApplyRowDecisions, useImportDuplicates } from '../hooks'

export function DuplicateReviewStep({ importId }: { importId: number }) {
  const toast = useToast()
  const duplicates = useImportDuplicates(importId)
  const decide = useApplyRowDecisions()

  if (duplicates.isLoading) return <LoadingSpinner label="Checking duplicates" />
  if (duplicates.isError) {
    return (
      <InlineError
        message={
          duplicates.error instanceof ApiError
            ? duplicates.error.message
            : 'Could not load the duplicate check'
        }
        onRetry={() => void duplicates.refetch()}
      />
    )
  }

  const groups = duplicates.data ?? []
  if (groups.length === 0) return null

  const duplicateRowIds = groups.flatMap((group) =>
    group.rows.filter((row) => row.status === 'DUPLICATE').map((row) => row.id),
  )
  const matchesExisting = groups.some((group) => group.existingTransactionIds.length > 0)

  const skipAll = (): void => {
    if (duplicateRowIds.length === 0) return
    decide.mutate(
      { id: importId, input: { rowIds: duplicateRowIds, decision: 'SKIP' } },
      {
        onSuccess: () =>
          toast.push({ tone: 'success', message: `${duplicateRowIds.length} duplicate rows skipped` }),
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message:
              cause instanceof ApiError ? cause.message : 'Could not skip the duplicate rows',
          }),
      },
    )
  }

  return (
    <Card>
      <CardHeader
        title="Possible duplicates"
        description={
          matchesExisting
            ? 'These rows match transactions that are already in your ledger.'
            : 'These rows repeat each other inside this file.'
        }
        actions={
          <Button
            variant="secondary"
            size="sm"
            loading={decide.isPending}
            disabled={duplicateRowIds.length === 0}
            onClick={skipAll}
          >
            Skip all duplicates
          </Button>
        }
      />
      <CardBody className="flex flex-col gap-4">
        {groups.map((group, index) => (
          <div
            key={group.dedupeHash}
            className="rounded-md border border-border bg-surface-sunken p-4"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-medium text-text">{group.rows.length} matching rows</p>
              {group.existingTransactionIds.length > 0 && (
                <Badge tone="warning">
                  Already in ledger: transaction {group.existingTransactionIds.join(', ')}
                </Badge>
              )}
            </div>
            <ul className="mt-3 flex flex-col gap-1 text-sm">
              {group.rows.map((row) => (
                <li key={row.id} className="flex flex-wrap items-center gap-2 text-muted">
                  <span className="text-text">Row {row.rowNumber}</span>
                  <span>{row.occurredOn ? formatBusinessDate(row.occurredOn) : '—'}</span>
                  <span>{formatMoney(row.amountMinor, row.currencyCode ?? 'USD')}</span>
                  {row.description && <span className="truncate">{row.description}</span>}
                </li>
              ))}
            </ul>
            <p className="mt-2 text-xs text-subtle">Group {index + 1} of {groups.length}</p>
          </div>
        ))}
      </CardBody>
    </Card>
  )
}
