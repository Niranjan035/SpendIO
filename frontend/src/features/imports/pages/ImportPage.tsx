import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardBody } from '@/components/ui/Card'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { IMPORT_STATUS_LABELS, importStatusTone, importStepForStatus } from '../labels'
import { useDeleteImport, useExecuteImport, useImport } from '../hooks'
import { ColumnMappingStep } from '../components/ColumnMappingStep'
import { DuplicateReviewStep } from '../components/DuplicateReviewStep'
import { ImportStepper } from '../components/ImportStepper'
import { ImportSummaryStep } from '../components/ImportSummaryStep'
import { PreviewTable } from '../components/PreviewTable'
import { UploadDropzone } from '../components/UploadDropzone'

function ProgressNotice({ label }: { label: string }) {
  return (
    <Card>
      <CardBody className="flex items-center gap-3">
        <LoadingSpinner label={label} />
        <p className="text-sm text-muted" aria-live="polite">
          {label}
        </p>
      </CardBody>
    </Card>
  )
}

export function ImportPage() {
  const params = useParams()
  const id = params.id !== undefined ? Number(params.id) : 0

  if (!Number.isFinite(id) || id <= 0) {
    return <UploadView />
  }

  return <DetailView importId={id} />
}

function UploadView() {
  const navigate = useNavigate()

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold text-text">Import a CSV file</h1>
        <p className="mt-1 text-sm text-muted">
          Map your bank export to SpendIO columns, review every row, then import.
        </p>
      </header>

      <UploadDropzone />

      <div className="flex justify-end">
        <Button variant="ghost" onClick={() => navigate('/import/history')}>
          View import history
        </Button>
      </div>
    </div>
  )
}

function DetailView({ importId }: { importId: number }) {
  const navigate = useNavigate()
  const toast = useToast()
  const jobQuery = useImport(importId)
  const execute = useExecuteImport()
  const remove = useDeleteImport()
  const [allowPartialImport, setAllowPartialImport] = useState(false)

  const job = jobQuery.data

  const failureMessage = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  if (jobQuery.isLoading) return <LoadingSpinner label="Loading import" />
  if (jobQuery.isError || !job) {
    return (
      <div className="flex flex-col gap-4">
        <InlineError
          message={
            jobQuery.error instanceof ApiError
              ? jobQuery.error.message
              : 'Could not load this import'
          }
          onRetry={() => void jobQuery.refetch()}
        />
        <div>
          <Button variant="secondary" onClick={() => navigate('/import/history')}>
            Back to history
          </Button>
        </div>
      </div>
    )
  }

  const step = importStepForStatus(job.status)
  const remaining =
    job.totals.total - job.totals.created - job.totals.failed - job.totals.skipped - job.totals.invalid

  const runExecute = (): void => {
    execute.mutate(
      { id: importId, input: { allowPartialImport } },
      {
        onSuccess: (report) => {
          const imported = report.import_.totals.created
          toast.push({ tone: 'success', message: `${imported} rows imported` })
        },
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: failureMessage(cause, 'Could not import the rows'),
          }),
      },
    )
  }

  const deleteImport = (): void => {
    remove.mutate(importId, {
      onSuccess: () => {
        toast.push({ tone: 'success', message: 'Import deleted' })
        navigate('/import/history')
      },
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: failureMessage(cause, 'Could not delete the import'),
        }),
    })
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold text-text">{job.originalFilename}</h1>
            <Badge tone={importStatusTone(job.status)}>{IMPORT_STATUS_LABELS[job.status]}</Badge>
          </div>
          <p className="mt-1 text-sm text-muted">
            {job.totals.total} rows · uploaded {new Date(job.createdAt).toLocaleDateString()}
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => navigate('/import/history')}>
            Back to history
          </Button>
          <Button
            variant="ghost"
            loading={remove.isPending}
            disabled={job.status === 'PROCESSING'}
            onClick={deleteImport}
          >
            Delete
          </Button>
        </div>
      </header>

      <ImportStepper current={step} />

      {step === 'upload' && <ProgressNotice label="Parsing the file…" />}
      {step === 'map' && <ColumnMappingStep importId={importId} />}
      {step === 'review' && (job.status === 'VALIDATING' || job.status === 'PROCESSING') && (
        <ProgressNotice
          label={job.status === 'VALIDATING' ? 'Validating rows…' : 'Importing rows…'}
        />
      )}
      {step === 'review' && job.status === 'READY' && (
        <>
          <DuplicateReviewStep importId={importId} />
          <PreviewTable importId={importId} readOnly={false} />
          <Card>
            <CardBody className="flex flex-col gap-4">
              <p className="text-sm text-muted">
                {remaining > 0
                  ? `${remaining} rows are ready to import. Skipped and invalid rows stay untouched.`
                  : 'Every row is skipped or invalid — fix rows in the preview before importing.'}
              </p>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <label className="flex items-center gap-2 text-sm text-text">
                  <input
                    type="checkbox"
                    checked={allowPartialImport}
                    onChange={(event) => setAllowPartialImport(event.target.checked)}
                  />
                  Allow partial import (finish even if some rows fail)
                </label>
                <Button loading={execute.isPending} disabled={remaining <= 0} onClick={runExecute}>
                  Start import
                </Button>
              </div>
            </CardBody>
          </Card>
        </>
      )}
      {step === 'done' && (
        <>
          <ImportSummaryStep job={job} />
          <PreviewTable importId={importId} readOnly />
        </>
      )}
    </div>
  )
}
