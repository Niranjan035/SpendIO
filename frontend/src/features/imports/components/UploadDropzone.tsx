import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { useTransactionFilterOptions } from '@/features/transactions/hooks'
import { formatBytes } from '../format'
import { useUploadImport } from '../hooks'
import {
  csvFileSchema,
  EMPTY_UPLOAD_FORM,
  uploadFormSchema,
  type UploadFormValues,
} from '../schemas'

const DATE_FORMAT_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: '', label: 'Auto-detect' },
  { value: 'yyyy-MM-dd', label: '2026-03-09 (yyyy-MM-dd)' },
  { value: 'dd/MM/yyyy', label: '09/03/2026 (dd/MM/yyyy)' },
  { value: 'MM/dd/yyyy', label: '03/09/2026 (MM/dd/yyyy)' },
  { value: 'dd MMM yyyy', label: '9 Mar 2026 (dd MMM yyyy)' },
]

export function UploadDropzone() {
  const navigate = useNavigate()
  const toast = useToast()
  const options = useTransactionFilterOptions()
  const upload = useUploadImport()
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<UploadFormValues>({
    resolver: zodResolver(uploadFormSchema),
    defaultValues: EMPTY_UPLOAD_FORM,
  })

  const pickFile = (next: File | null | undefined): void => {
    if (!next) return
    const result = csvFileSchema.safeParse(next)
    if (!result.success) {
      setFile(null)
      setFileError(result.error.issues[0]?.message ?? 'Choose a CSV file')
      return
    }
    setFile(next)
    setFileError(null)
  }

  const failureMessage = (cause: unknown, fallback: string): string =>
    cause instanceof ApiError ? cause.message : fallback

  const onSubmit = handleSubmit((values) => {
    if (!file) {
      setFileError('Choose a CSV file')
      return
    }
    upload.mutate(
      {
        file,
        defaultCurrencyCode: values.defaultCurrencyCode || undefined,
        defaultDateFormat: values.defaultDateFormat || undefined,
        targetAccountId: values.targetAccountId ? Number(values.targetAccountId) : undefined,
      },
      {
        onSuccess: (job) => {
          toast.push({ tone: 'success', message: 'File uploaded — check the column mapping' })
          navigate(`/import/${job.id}`)
        },
        onError: (cause) =>
          toast.push({
            tone: 'error',
            message: failureMessage(cause, 'Could not upload the CSV file'),
          }),
      },
    )
  })

  const accounts = (options.data?.accounts ?? []).filter(
    (account) => account.status === 'ACTIVE',
  )
  const currencies = options.data?.currencies ?? []

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div
        onDragOver={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragging(false)
          pickFile(event.dataTransfer.files[0])
        }}
        className={`rounded-lg border-2 border-dashed p-10 text-center transition-colors ${
          dragging ? 'border-primary bg-primary-subtle' : 'border-border-strong bg-surface'
        }`}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          className="sr-only"
          aria-label="CSV file"
          onChange={(event) => pickFile(event.target.files?.[0])}
        />
        <p className="text-sm font-medium text-text">Drag a CSV file here</p>
        <p className="mt-1 text-sm text-muted">or</p>
        <div className="mt-3">
          <Button variant="secondary" onClick={() => inputRef.current?.click()}>
            Choose file
          </Button>
        </div>
        {file && (
          <p className="mt-4 text-sm text-text">
            {file.name} <span className="text-muted">({formatBytes(file.size)})</span>
          </p>
        )}
        {fileError && (
          <p role="alert" className="mt-4 text-sm text-danger">
            {fileError}
          </p>
        )}
        <p className="mt-4 text-xs text-subtle">Comma-separated .csv files up to 5 MB.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Select label="Default currency" hint="When the file has no currency column" error={errors.defaultCurrencyCode?.message} {...register('defaultCurrencyCode')}>
          <option value="">Auto</option>
          {currencies.map((currency) => (
            <option key={currency} value={currency}>
              {currency}
            </option>
          ))}
        </Select>
        <Select label="Target account" hint="When the file has no account column" error={errors.targetAccountId?.message} {...register('targetAccountId')}>
          <option value="">Match per row</option>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name}
            </option>
          ))}
        </Select>
        <Select label="Date format" error={errors.defaultDateFormat?.message} {...register('defaultDateFormat')}>
          {DATE_FORMAT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </div>

      <div className="flex justify-end">
        <Button type="submit" loading={upload.isPending} disabled={!file}>
          Upload and parse
        </Button>
      </div>
    </form>
  )
}
