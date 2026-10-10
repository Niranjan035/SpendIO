import { useState } from 'react'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import { formatDeltaPct, presetRange, RANGE_PRESETS } from '../labels'
import { useTopSpending } from '../hooks'
import { TrendRangeSelect, type TrendSelection } from './TrendRangeSelect'

export interface TopSpendingTableProps {
  currency: string
}

export function TopSpendingTable({ currency }: TopSpendingTableProps) {
  const [selection, setSelection] = useState<TrendSelection>(() => {
    const preset = RANGE_PRESETS[2]!
    return { ...presetRange(preset), granularity: preset.granularity }
  })
  const { data, isLoading, isError } = useTopSpending(selection, 10)

  const rows = data ?? []
  const isEmpty = !isLoading && !isError && rows.length === 0

  return (
    <Card>
      <CardHeader
        title="Top spending"
        description="Largest categories with the change vs the previous period"
        actions={<TrendRangeSelect value={selection} onChange={setSelection} />}
      />
      <CardBody className="p-0">
        {isLoading && <p className="p-4 text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="p-4 text-sm text-muted">No spending in this range.</p>}
        {!isLoading && !isEmpty && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-subtle">
                  <th className="px-4 py-2 font-medium">#</th>
                  <th className="px-4 py-2 font-medium">Category</th>
                  <th className="px-4 py-2 text-right font-medium">Amount</th>
                  <th className="px-4 py-2 text-right font-medium">Share</th>
                  <th className="px-4 py-2 text-right font-medium">vs previous</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const delta = row.deltaPct
                  const hasDelta = delta !== null && delta !== undefined
                  const positive = hasDelta && delta > 0
                  const unknown = !hasDelta
                  return (
                    <tr key={row.categoryId ?? `uncategorized-${row.rank}`} className="border-b border-border last:border-0">
                      <td className="px-4 py-2.5 text-muted num">{row.rank}</td>
                      <td className="px-4 py-2.5 font-medium text-text">
                        {row.name ?? 'Uncategorized'}
                      </td>
                      <td className="px-4 py-2.5 text-right text-text num">
                        {formatMoney(row.amountMinor, currency)}
                      </td>
                      <td className="px-4 py-2.5 text-right text-muted num">
                        {row.sharePct.toFixed(1)}%
                      </td>
                      <td className="px-4 py-2.5 text-right">
                        <Badge
                          tone={unknown ? 'neutral' : positive ? 'danger' : 'success'}
                          className={cn(!unknown && (positive ? 'text-danger' : 'text-success'))}
                        >
                          {formatDeltaPct(delta)}
                        </Badge>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </CardBody>
    </Card>
  )
}