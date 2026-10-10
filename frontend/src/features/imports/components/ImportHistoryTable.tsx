import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { Card, CardBody } from '@/components/ui/Card'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { formatBusinessDate } from '@/lib/dates'
import { IMPORT_STATUSES, type ImportStatus } from '@/types/enums'
import { IMPORT_STATUS_LABELS, importStatusTone } from '../labels'
import { useDeleteImport, useImportHistory } from '../hooks'
import type { ImportSummary } from '../types'

const PAGE_SIZE = 20

export function ImportHistoryTable() {
  const navigate = useNavigate()
  const toast = useToast()
  const [status, setStatus] = useState<ImportStatus | ''>('')
  const [page, setPage] = useState(0)
  const [deleting, setDeleting] = useState<ImportSummary | null>(null)

  const history = useImportHistory({ status: status || undefined, page, size: PAGE_SIZE })
  const remove = useDeleteImport()

  const changeFilter = (next: ImportStatus | ''): void => {
    setStatus(next)
    setPage(0)
  }

  const confirmDelete = (): void => {
    if (!deleting) return
    remove.mutate(deleting.id, {
      onSuccess: () => {
        toast.push({ tone: 'success', message: 'Import deleted' })
        setDeleting(null)
      },
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'Could not delete the import',
        }),
    })
  }

  if (history.isLoading) return <LoadingSpinner label="Loading imports" />
  if (history.isError) {
    return (
      <InlineError
        message={
          history.error instanceof ApiError ? history.error.message : 'Could not load the imports'
        }
        onRetry={() => void history.refetch()}
      />
    )
  }

  const data = history.data

  return (
    <Card>
      <CardBody className="flex flex-col gap-4">
        <Select
          label="Status"
          className="w-56"
          value={status}
          onChange={(event) => changeFilter(event.target.value as ImportStatus | '')}
        >
          <option value="">All statuses</option>
          {IMPORT_STATUSES.map((importStatus) => (
            <option key={importStatus} value={importStatus}>
              {IMPORT_STATUS_LABELS[importStatus]}
            </option>
          ))}
        </Select>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase text-subtle">
                <th scope="col" className="py-2 pr-4 font-medium">
                  File
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Uploaded
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Status
                </th>
                <th scope="col" className="py-2 pr-4 text-right font-medium">
                  Rows
                </th>
                <th scope="col" className="py-2 pr-4 text-right font-medium">
                  Imported
                </th>
                <th scope="col" className="py-2 font-medium">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {(!data || data.content.length === 0) && (
                <tr>
                  <td colSpan={6} className="py-6 text-center text-muted">
                    No imports yet.
                  </td>
                </tr>
              )}
              {data?.content.map((job) => (
                <tr key={job.id} className="border-b border-border/60">
                  <td className="py-3 pr-4">
                    <button
                      type="button"
                      className="font-medium text-primary hover:underline"
                      onClick={() => navigate(`/import/${job.id}`)}
                    >
                      {job.originalFilename}
                    </button>
                  </td>
                  <td className="py-3 pr-4 text-muted">{formatBusinessDate(job.createdAt)}</td>
                  <td className="py-3 pr-4">
                    <Badge tone={importStatusTone(job.status)}>
                      {IMPORT_STATUS_LABELS[job.status]}
                    </Badge>
                  </td>
                  <td className="py-3 pr-4 text-right text-text">{job.totals.total}</td>
                  <td className="py-3 pr-4 text-right text-text">{job.totals.created}</td>
                  <td className="py-3 text-right">
                    <Button variant="ghost" size="sm" onClick={() => setDeleting(job)}>
                      Delete
                    </Button>
                  </td>
                </tr>
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
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Previous
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={data.last}
                onClick={() => setPage((current) => current + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </CardBody>

      <Modal
        open={deleting !== null}
        title="Delete import"
        onClose={() => setDeleting(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={remove.isPending} onClick={confirmDelete}>
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted">
          Delete the import record for{' '}
          <span className="font-medium text-text">{deleting?.originalFilename}</span>? Imported
          transactions stay in the ledger.
        </p>
      </Modal>
    </Card>
  )
}
