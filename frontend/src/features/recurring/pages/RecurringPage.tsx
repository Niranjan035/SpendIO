import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { RECURRING_STATUSES } from '@/types/enums'
import { RECURRING_STATUS_LABELS } from '../labels'
import { useRecurrings } from '../hooks'
import type { RecurringFilter, RecurringTransaction } from '../types'
import { RecurringCard } from '../components/RecurringCard'
import { RecurringFormModal } from '../components/RecurringFormModal'

const DUE_WITHIN_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: '', label: 'Any due date' },
  { value: '7', label: 'Due within 7 days' },
  { value: '30', label: 'Due within 30 days' },
  { value: '90', label: 'Due within 90 days' },
]

export function RecurringPage() {
  const [status, setStatus] = useState<string>('')
  const [dueWithinDays, setDueWithinDays] = useState<string>('')
  const [editing, setEditing] = useState<RecurringTransaction | null>(null)
  const [creating, setCreating] = useState(false)

  const filter: RecurringFilter = useMemo(
    () => ({
      status: status === '' ? undefined : (status as RecurringFilter['status']),
      dueWithinDays: dueWithinDays === '' ? undefined : Number(dueWithinDays),
      page: 0,
      size: 50,
    }),
    [status, dueWithinDays],
  )

  const rules = useRecurrings(filter)
  const content = rules.data?.content ?? []

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Recurring rules</h1>
          <p className="mt-1 text-sm text-muted">
            Automated transactions: set a schedule once and SpendIO generates each occurrence.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>New rule</Button>
      </header>

      <div className="flex flex-wrap gap-4">
        <div className="w-52">
          <Select
            label="Status"
            value={status}
            onChange={(event) => setStatus(event.target.value)}
            aria-label="Filter by status"
          >
            <option value="">All statuses</option>
            {RECURRING_STATUSES.map((option) => (
              <option key={option} value={option}>
                {RECURRING_STATUS_LABELS[option]}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-52">
          <Select
            label="Due"
            value={dueWithinDays}
            onChange={(event) => setDueWithinDays(event.target.value)}
            aria-label="Filter by due date"
          >
            {DUE_WITHIN_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {rules.isLoading && (
        <div className="flex justify-center py-10">
          <LoadingSpinner label="Loading recurring rules" />
        </div>
      )}

      {rules.isError && (
        <p role="alert" className="text-sm text-danger">
          Could not load recurring rules.
        </p>
      )}

      {!rules.isLoading && !rules.isError && content.length === 0 && (
        <div className="rounded-lg border border-border bg-surface p-10 text-center">
          <p className="text-sm text-muted">No recurring rules yet.</p>
          <p className="mt-1 text-sm text-subtle">Create one to automate a regular income or expense.</p>
        </div>
      )}

      {content.length > 0 && (
        <section aria-label="Recurring rules" className="grid gap-4 xl:grid-cols-2">
          {content.map((rule) => (
            <RecurringCard key={rule.id} rule={rule} onEdit={setEditing} />
          ))}
        </section>
      )}

      <RecurringFormModal
        rule={editing}
        open={creating || editing !== null}
        onClose={() => {
          setCreating(false)
          setEditing(null)
        }}
      />
    </div>
  )
}