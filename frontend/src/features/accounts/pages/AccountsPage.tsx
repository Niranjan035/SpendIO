import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, CardBody } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Select } from '@/components/ui/Select'
import { Input } from '@/components/ui/Input'
import { LoadingSpinner, InlineError } from '@/components/feedback/LoadingSpinner'
import { formatMoney } from '@/lib/money'
import { ApiError } from '@/services/http/ApiError'
import { ACCOUNT_TYPES } from '@/types/enums'
import type { AccountStatus, AccountType } from '@/types/enums'
import { ACCOUNT_TYPE_LABELS, accountTypeColor } from '../labels'
import { useAccounts, useAccountSummary } from '../hooks'
import { AccountFormModal } from '../components/AccountFormModal'
import type { AccountFilter } from '../types'

export function AccountsPage() {
  const [creating, setCreating] = useState(false)
  const [type, setType] = useState<AccountType | ''>('')
  const [status, setStatus] = useState<AccountStatus | ''>('ACTIVE')
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQ(q.trim()), 300)
    return () => clearTimeout(timer)
  }, [q])

  const filter: AccountFilter = {
    type: type === '' ? undefined : type,
    status: status === '' ? undefined : status,
    q: debouncedQ || undefined,
    page,
    size: 50,
  }

  const accounts = useAccounts(filter)
  const summary = useAccountSummary()

  const pushFilter = (patch: Partial<AccountFilter>): void => {
    setPage(patch.page ?? 0)
    if (patch.type !== undefined) setType(patch.type ?? '')
    if (patch.status !== undefined) setStatus(patch.status ?? '')
    if (patch.q !== undefined) setDebouncedQ(patch.q)
  }

  const totalMinor = summary.data?.totalBaseMinor
  const baseCurrency = summary.data?.baseCurrencyCode ?? 'USD'

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-text">Accounts</h1>
          <p className="mt-1 text-sm text-muted">
            Bank accounts, cards and wallets. Create an account to start tracking a balance.
          </p>
        </div>
        <Button onClick={() => setCreating(true)}>New account</Button>
      </header>

      <section aria-label="Totals" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardBody>
            <p className="text-xs uppercase tracking-wide text-subtle">Total balance</p>
            <p className="mt-2 text-2xl font-semibold text-text num">
              {summary.isLoading
                ? '—'
                : formatMoney(totalMinor ?? 0, baseCurrency, undefined, 'en-US')}
            </p>
            {summary.isError && <p className="mt-1 text-xs text-danger">Could not load totals.</p>}
          </CardBody>
        </Card>
        {ACCOUNT_TYPES.map((accountType) => {
          const amount = summary.data?.byType[accountType]
          return (
            <Card key={accountType}>
              <CardBody>
                <p className="text-xs uppercase tracking-wide text-subtle">
                  {ACCOUNT_TYPE_LABELS[accountType]}
                </p>
                <p className="mt-2 text-lg font-semibold text-text num">
                  {amount === undefined
                    ? '—'
                    : formatMoney(amount, baseCurrency, undefined, 'en-US')}
                </p>
              </CardBody>
            </Card>
          )
        })}
      </section>

      <section aria-label="Filters" className="grid gap-4 sm:grid-cols-3">
        <Input
          label="Search"
          placeholder="Name or institution"
          value={q}
          onChange={(event) => {
            setQ(event.target.value)
            pushFilter({ page: 0 })
          }}
        />
        <Select
          label="Type"
          value={type}
          onChange={(event) => {
            setType(event.target.value as AccountType | '')
            setPage(0)
          }}
        >
          <option value="">All types</option>
          {ACCOUNT_TYPES.map((option) => (
            <option key={option} value={option}>
              {ACCOUNT_TYPE_LABELS[option]}
            </option>
          ))}
        </Select>
        <Select
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as AccountStatus | '')
            setPage(0)
          }}
        >
          <option value="">All</option>
          <option value="ACTIVE">Active</option>
          <option value="ARCHIVED">Archived</option>
        </Select>
      </section>

      <section aria-label="Account list" className="flex flex-col gap-3">
        {accounts.isLoading && (
          <p className="flex items-center gap-2 text-sm text-muted">
            <LoadingSpinner label="Loading accounts" /> Loading accounts…
          </p>
        )}
        {accounts.isError && (
          <InlineError
            message={
              accounts.error instanceof ApiError
                ? accounts.error.message
                : 'Could not load accounts.'
            }
            onRetry={() => void accounts.refetch()}
          />
        )}
        {accounts.data && accounts.data.content.length === 0 && (
          <Card>
            <CardBody>
              <p className="text-sm text-muted">No accounts match these filters yet.</p>
            </CardBody>
          </Card>
        )}
        {accounts.data?.content.map((account) => (
          <Link
            key={account.id}
            to={`/accounts/${account.id}`}
            className="block rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <Card className="hover:border-border-strong">
              <CardBody className="flex items-center gap-4">
                <span
                  aria-hidden="true"
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: account.color ?? accountTypeColor(account.type) }}
                />
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-text">
                    {account.name}
                    {account.primary && <Badge tone="primary">Primary</Badge>}
                    {account.status === 'ARCHIVED' && <Badge tone="neutral">Archived</Badge>}
                  </p>
                  <p className="mt-0.5 truncate text-xs text-muted">
                    {ACCOUNT_TYPE_LABELS[account.type]}
                    {account.institution ? ` · ${account.institution}` : ''}
                    {account.transactionCount > 0
                      ? ` · ${account.transactionCount} transaction${account.transactionCount === 1 ? '' : 's'}`
                      : ''}
                  </p>
                </div>
                <p className="text-right">
                  <span className="block text-sm font-semibold text-text num">
                    {formatMoney(account.balanceMinor, account.currencyCode, undefined, 'en-US')}
                  </span>
                  <span className="block text-xs text-muted num">
                    {account.baseCurrencyCode !== account.currencyCode &&
                      formatMoney(
                        account.balanceBaseMinor,
                        account.baseCurrencyCode,
                        undefined,
                        'en-US',
                      )}
                  </span>
                </p>
              </CardBody>
            </Card>
          </Link>
        ))}
        {accounts.data && accounts.data.totalPages > 1 && (
          <div className="flex items-center justify-between">
            <Button
              variant="secondary"
              size="sm"
              disabled={accounts.data.first}
              onClick={() => pushFilter({ page: page - 1 })}
            >
              Previous
            </Button>
            <p className="text-xs text-muted">
              Page {accounts.data.page + 1} of {accounts.data.totalPages}
            </p>
            <Button
              variant="secondary"
              size="sm"
              disabled={accounts.data.last}
              onClick={() => pushFilter({ page: page + 1 })}
            >
              Next
            </Button>
          </div>
        )}
      </section>

      <AccountFormModal account={null} open={creating} onClose={() => setCreating(false)} />
    </div>
  )
}
