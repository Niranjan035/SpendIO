import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Select } from '@/components/ui/Select'
import { InlineError, LoadingSpinner } from '@/components/feedback/LoadingSpinner'
import { Modal } from '@/components/ui/Modal'
import { formatBusinessDate, lastNDaysRange } from '@/lib/dates'
import { formatMoney } from '@/lib/money'
import { ApiError } from '@/services/http/ApiError'
import { useToast } from '@/app/providers/ToastProvider'
import { ACCOUNT_STATUS_LABELS, ACCOUNT_TYPE_LABELS, accountTypeColor } from '../labels'
import {
  useAccount,
  useAccountBalance,
  useArchiveAccount,
  useDailyBalances,
  useDeleteAccount,
  useRestoreAccount,
  useSetPrimaryAccount,
} from '../hooks'
import { AccountFormModal } from '../components/AccountFormModal'
import { AdjustBalanceForm } from '../components/AdjustBalanceForm'

const RANGE_OPTIONS = [
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
] as const

export function AccountDetailPage() {
  const { id: rawId } = useParams<{ id: string }>()
  const id = Number(rawId)
  const navigate = useNavigate()
  const toast = useToast()

  const [editing, setEditing] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [rangeDays, setRangeDays] = useState(30)
  const range = lastNDaysRange(rangeDays)

  const account = useAccount(id)
  const balance = useAccountBalance(id)
  const series = useDailyBalances(id, range.from, range.to)

  const archive = useArchiveAccount()
  const restore = useRestoreAccount()
  const setPrimary = useSetPrimaryAccount()
  const removeAccount = useDeleteAccount()

  const run = (action: { mutate: typeof archive.mutate }, idToRun: number, success: string): void =>
    action.mutate(idToRun, {
      onSuccess: () => toast.push({ tone: 'success', message: success }),
      onError: (cause) =>
        toast.push({
          tone: 'error',
          message: cause instanceof ApiError ? cause.message : 'That did not work',
        }),
    })

  if (account.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted">
        <LoadingSpinner label="Loading account" /> Loading account…
      </p>
    )
  }

  if (account.isError || !account.data) {
    return (
      <InlineError
        message={
          account.error instanceof ApiError ? account.error.message : 'Could not load the account.'
        }
        onRetry={() => void account.refetch()}
      />
    )
  }

  const current = account.data
  const currentBalance = balance.data

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className="mt-1.5 h-3 w-3 shrink-0 rounded-full"
            style={{ backgroundColor: current.color ?? accountTypeColor(current.type) }}
          />
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold text-text">{current.name}</h1>
              {current.primary && <Badge tone="primary">Primary</Badge>}
              <Badge tone={current.status === 'ARCHIVED' ? 'neutral' : 'success'}>
                {ACCOUNT_STATUS_LABELS[current.status]}
              </Badge>
            </div>
            <p className="mt-1 text-sm text-muted">
              {ACCOUNT_TYPE_LABELS[current.type]}
              {current.institution ? ` · ${current.institution}` : ''}
              {current.last4 ? ` · •••• ${current.last4}` : ''}
            </p>
            <button
              type="button"
              onClick={() => navigate('/accounts')}
              className="mt-2 text-sm text-primary hover:underline"
            >
              ← All accounts
            </button>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            Edit
          </Button>
          {!current.primary && current.status === 'ACTIVE' && (
            <Button
              variant="secondary"
              size="sm"
              loading={setPrimary.isPending}
              onClick={() => run(setPrimary, current.id, 'Primary account updated')}
            >
              Make primary
            </Button>
          )}
          {current.status === 'ACTIVE' ? (
            <Button
              variant="secondary"
              size="sm"
              loading={archive.isPending}
              onClick={() => run(archive, current.id, 'Account archived')}
            >
              Archive
            </Button>
          ) : (
            <Button
              variant="secondary"
              size="sm"
              loading={restore.isPending}
              onClick={() => run(restore, current.id, 'Account restored')}
            >
              Restore
            </Button>
          )}
          <Button variant="danger" size="sm" onClick={() => setConfirmingDelete(true)}>
            Delete
          </Button>
        </div>
      </header>

      <section aria-label="Balance" className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Current balance</p>
            <p className="mt-2 text-3xl font-semibold text-text num">
              {currentBalance
                ? formatMoney(currentBalance.balanceMinor, currentBalance.currencyCode)
                : '—'}
            </p>
            <p className="mt-1 text-xs text-muted num">
              {currentBalance && currentBalance.baseCurrencyCode !== currentBalance.currencyCode
                ? `≈ ${formatMoney(currentBalance.balanceBaseMinor, currentBalance.baseCurrencyCode)}`
                : formatBusinessDate(currentBalance?.asOf) === '—'
                  ? 'No balance recorded yet'
                  : `As of ${formatBusinessDate(currentBalance?.asOf)}`}
            </p>
            {current.availableCreditMinor !== null &&
              current.availableCreditMinor !== undefined && (
                <p className="mt-3 border-t border-border pt-3 text-sm">
                  <span className="text-muted">Available credit: </span>
                  <span className="font-semibold text-text num">
                    {formatMoney(current.availableCreditMinor, current.currencyCode)}
                  </span>
                </p>
              )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Details" />
          <CardBody>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              <div>
                <dt className="text-xs text-muted">Opening balance</dt>
                <dd className="mt-0.5 text-text num">
                  {formatMoney(current.openingBalanceMinor, current.currencyCode)}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Opened</dt>
                <dd className="mt-0.5 text-text">
                  {formatBusinessDate(current.openingBalanceDate)}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Transactions</dt>
                <dd className="mt-0.5 text-text">{current.transactionCount}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Created</dt>
                <dd className="mt-0.5 text-text">{current.createdAt.slice(0, 10)}</dd>
              </div>
            </dl>
            {current.notes && <p className="mt-3 text-sm text-muted">{current.notes}</p>}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Balance history" />
          <CardBody>
            <Select
              label="Range"
              value={String(rangeDays)}
              onChange={(event) => setRangeDays(Number(event.target.value))}
            >
              {RANGE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
            <div className="mt-3 max-h-64 overflow-auto">
              {series.isLoading && <p className="text-sm text-muted">Loading series…</p>}
              {series.isError && (
                <p role="alert" className="text-sm text-danger">
                  Could not load the balance series.
                </p>
              )}
              {series.data &&
                (series.data.length === 0 ? (
                  <p className="text-sm text-muted">No balance history in this range.</p>
                ) : (
                  <ul className="divide-y divide-border text-sm">
                    {series.data.map((point) => (
                      <li key={point.date} className="flex items-center justify-between py-1.5">
                        <span className="text-muted">{formatBusinessDate(point.date)}</span>
                        <span className="num font-medium text-text">
                          {formatMoney(point.balanceMinor, current.currencyCode)}
                        </span>
                      </li>
                    ))}
                  </ul>
                ))}
            </div>
          </CardBody>
        </Card>
      </section>

      <section aria-label="Adjust balance">
        <Card>
          <CardHeader title="Adjust balance" />
          <CardBody>
            <AdjustBalanceForm account={current} balance={currentBalance} />
          </CardBody>
        </Card>
      </section>

      <AccountFormModal account={current} open={editing} onClose={() => setEditing(false)} />

      <Modal
        open={confirmingDelete}
        title="Delete account"
        onClose={() => setConfirmingDelete(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmingDelete(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={removeAccount.isPending}
              onClick={() => {
                removeAccount.mutate(
                  { id: current.id, force: current.transactionCount > 0 },
                  {
                    onSuccess: () => {
                      toast.push({ tone: 'success', message: 'Account deleted' })
                      void navigate('/accounts')
                    },
                    onError: (cause) =>
                      toast.push({
                        tone: 'error',
                        message:
                          cause instanceof ApiError
                            ? cause.message
                            : 'Could not delete the account',
                      }),
                  },
                )
              }}
            >
              Delete account
            </Button>
          </>
        }
      >
        <p className="text-sm text-text">
          {current.transactionCount > 0 ? (
            <>
              This account has <strong>{current.transactionCount} transactions</strong>. Deleting it
              permanently removes them as well. This cannot be undone.
            </>
          ) : (
            'This will permanently remove the account. This cannot be undone.'
          )}
        </p>
      </Modal>
    </div>
  )
}
