import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { Button } from '@/components/ui/Button'
import { useExportData } from '../hooks'

function downloadJson(exportedAt: string, data: Record<string, unknown>): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `spendio-export-${exportedAt.slice(0, 10)}.json`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

/** POST /users/me/export (ARCHITECTURE.md E.3, Phase 14). */
export function DataExportCard() {
  const exportData = useExportData()
  const toast = useToast()

  const onExport = () =>
    exportData.mutate(undefined, {
      onSuccess: (payload) => {
        downloadJson(payload.exportedAt, payload.data)
        toast.push({ tone: 'success', message: 'Your data export has been downloaded' })
      },
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'Could not build the export',
        }),
    })

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted">
        Download a complete JSON copy of your profile, preferences, accounts, transactions,
        budgets, goals and imports.
      </p>
      <div className="flex justify-end">
        <Button loading={exportData.isPending} onClick={onExport}>
          Export my data
        </Button>
      </div>
    </div>
  )
}
