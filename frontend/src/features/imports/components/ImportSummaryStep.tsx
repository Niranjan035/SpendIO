import { useState } from 'react'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatBusinessDate } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { IMPORT_STATUS_LABELS, importStatusTone } from '../labels'
import { importsApi } from '../api'
import { useImportReport, useUndoImport } from '../hooks'
import type { ImportSummary } from '../types'
import { ErrorReportTable } from './ErrorReportTable'

const SHOWN_TRANSACTIONS = 20

const TOTAL_LABELS: ReadonlyArray<{ key: keyof ImportSummary['totals']; label: string }> = [
  { key: 'total', label: 'Rows' },
  { key: 'valid', label: 'Valid' },
  { key: 'invalid', label: 'Invalid' },
  { key: 'duplicate', label: 'Duplicates' },
  { key: 'created', label: 'Imported' },
  { key: 'skipped', label: 'Skipped' },
  { key: 'failed', label: 'Failed' },
]

export function ImportSummaryStep({ job }: { job: ImportSummary }) {
  const toast = useToast()
  const report = useImportReport(job.id)
  const undo = useUndoImport()
  const [downloading, setDownloading] = useState(false)

  const canUndo = job.status === 'COMPLETED' || job.status === 'COMPLETED_WITH_ERRORS'
  const canDownloadErrors = job.totals.invalid > 0 || job.totals.failed > 0

  const downloadErrors = async (): Promise<void> => {
    setDownloading(true)
    try {
      const { blob, filename } = await importsApi.errorsCsv(job.id)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (cause) {
      toast.push({
        tone: 'error',
        message:
          cause instanceof ApiError ? cause.message : 'Could not download the error report',
      })
    } finally {
      setDownloading(false)
    }
  }

  const runUndo = (): void => {
    undo.mutate(job.id, {
      onSuccess: (result) =>
        toast.push({ tone: 'success', message: `${result.undone} imported rows undone` }),
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'Could not undo the import',
        }),
    })
  }

  const failures = report.data?.failures ?? []
  const created = report.data?.createdTransactions ?? []

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader
          title={job.originalFilename}
          description={job.completedAt ? `Finished ${formatBusinessDate(job.completedAt)}` : undefined}
          actions={<Badge tone={importStatusTone(job.status)}>{IMPORT_STATUS_LABELS[job.status]}</Badge>}
        />
        <CardBody className="flex flex-col gap-4">
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-7">
            {TOTAL_LABELS.map(({ key, label }) => (
              <div key={key}>
                <dt className="text-xs uppercase text-subtle">{label}</dt>
                <dd className="mt-1 text-lg font-semibold text-text">{job.totals[key]}</dd>
              </div>
            ))}
          </dl>

          {job.errorSummary && (
            <p role="alert" className="rounded-md border border-danger/40 bg-danger-subtle p-3 text-sm text-danger">
              {job.errorSummary}
            </p>
          )}

          <div className="flex flex-wrap gap-3">
            {canDownloadErrors && (
              <Button variant="secondary" loading={downloading} onClick={() => void downloadErrors()}>
                Download error report
              </Button>
            )}
            {canUndo && job.totals.created > 0 && (
              <Button variant="danger" loading={undo.isPending} onClick={runUndo}>
                Undo import
              </Button>
            )}
          </div>
        </CardBody>
      </Card>

      {created.length > 0 && (
        <Card>
          <CardHeader
            title="Imported transactions"
            description={`${created.length} transactions were created.`}
          />
          <CardBody>
            <ul className="flex flex-col gap-2 text-sm">
              {created.slice(0, SHOWN_TRANSACTIONS).map((transaction) => (
                <li key={transaction.id} className="flex flex-wrap items-center gap-3">
                  <span className="text-muted">{formatBusinessDate(transaction.occurredOn)}</span>
                  <span className="text-text">{transaction.description ?? '—'}</span>
                  <span className="ml-auto text-text">
                    {formatMoney(transaction.amountMinor, transaction.currencyCode)}
                  </span>
                </li>
              ))}
            </ul>
            {created.length > SHOWN_TRANSACTIONS && (
              <p className="mt-3 text-xs text-subtle">
                Showing the first {SHOWN_TRANSACTIONS} of {created.length}.
              </p>
            )}
          </CardBody>
        </Card>
      )}

      {failures.length > 0 && <ErrorReportTable failures={failures} />}
    </div>
  )
}
