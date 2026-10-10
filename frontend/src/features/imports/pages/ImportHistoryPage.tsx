import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/Button'
import { ImportHistoryTable } from '../components/ImportHistoryTable'

export function ImportHistoryPage() {
  const navigate = useNavigate()

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Import history</h1>
          <p className="mt-1 text-sm text-muted">
            Every CSV uploaded into SpendIO, newest first.
          </p>
        </div>
        <Button onClick={() => navigate('/import')}>New import</Button>
      </header>

      <ImportHistoryTable />
    </div>
  )
}
