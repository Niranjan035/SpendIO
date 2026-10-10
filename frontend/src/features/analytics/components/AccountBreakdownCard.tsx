import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatMoney } from '@/lib/money'
import { ACCOUNT_TYPE_LABELS } from '../labels'
import { useAccountBreakdown } from '../hooks'
import { tooltipStyle, useChartPalette } from './chartPalette'

export function AccountBreakdownCard() {
  const { data, isLoading, isError } = useAccountBreakdown()
  const palette = useChartPalette()

  const breakdown = data
  const isEmpty = !isLoading && !isError && (!breakdown || breakdown.accounts.length === 0)
  const pieData =
    breakdown?.accounts.map((account, index) => ({
      name: account.name,
      value: account.balanceBaseMinor,
      fill: palette.colors[index % palette.colors.length],
    })) ?? []

  return (
    <Card>
      <CardHeader
        title="Accounts"
        description="Balance by account and by type"
      />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No accounts yet.</p>}
        {breakdown && !isEmpty && (
          <div className="flex flex-col items-center gap-6 lg:flex-row lg:items-start">
            <div role="img" aria-label="Balance by account" className="h-56 w-56 shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Tooltip
                    contentStyle={tooltipStyle(palette)}
                    formatter={(value: unknown) => formatMoney(Number(value), breakdown.baseCurrencyCode)}
                  />
                  <Pie
                    data={pieData}
                    dataKey="value"
                    nameKey="name"
                    innerRadius={48}
                    outerRadius={80}
                    paddingAngle={2}
                    stroke="none"
                  >
                    {pieData.map((entry) => (
                      <Cell key={entry.name} fill={entry.fill} />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="min-w-0 flex-1 space-y-5">
              <div>
                <p className="text-xs uppercase tracking-wide text-subtle">Total</p>
                <p className="mt-0.5 text-xl font-semibold text-text num">
                  {formatMoney(breakdown.totalBaseMinor, breakdown.baseCurrencyCode)}
                </p>
              </div>
              <ul className="flex flex-wrap gap-2">
                {breakdown.byType.map((type) => (
                  <li
                    key={type.type}
                    className="rounded-md border border-border bg-surface-sunken px-2 py-1 text-xs"
                  >
                    <span className="text-muted">{ACCOUNT_TYPE_LABELS[type.type] ?? type.type}: </span>
                    <span className="font-medium text-text num">
                      {formatMoney(type.totalBaseMinor, breakdown.baseCurrencyCode)}
                    </span>
                  </li>
                ))}
              </ul>
              <ul className="grid gap-1.5 sm:grid-cols-2">
                {breakdown.accounts.map((account, index) => (
                  <li key={account.accountId} className="flex items-center gap-2 text-sm">
                    <span
                      aria-hidden
                      className="h-2.5 w-2.5 shrink-0 rounded-full"
                      style={{ background: palette.colors[index % palette.colors.length] }}
                    />
                    <span className="min-w-0 flex-1 truncate text-text">{account.name}</span>
                    <span className="text-muted num">{formatMoney(account.balanceBaseMinor, breakdown.baseCurrencyCode)}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </CardBody>
    </Card>
  )
}