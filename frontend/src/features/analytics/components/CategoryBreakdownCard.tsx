import { useState } from 'react'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { Select } from '@/components/ui/Select'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatMoney } from '@/lib/money'
import { presetRange, RANGE_PRESETS } from '../labels'
import { useCategoryBreakdown } from '../hooks'
import { tooltipStyle, useChartPalette } from './chartPalette'
import { TrendRangeSelect, type TrendSelection } from './TrendRangeSelect'

const SLICES = 8

export interface CategoryBreakdownCardProps {
  currency: string
}

export function CategoryBreakdownCard({ currency }: CategoryBreakdownCardProps) {
  const [selection, setSelection] = useState<TrendSelection>(() => {
    const preset = RANGE_PRESETS[2]!
    return { ...presetRange(preset), granularity: preset.granularity }
  })
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE')
  const { data, isLoading, isError } = useCategoryBreakdown(selection, type, 50, false)
  const palette = useChartPalette()

  const items = data ?? []
  const isEmpty = !isLoading && !isError && items.length === 0
  const shown = items.slice(0, SLICES)
  const pieData = shown.map((item, index) => ({
    name: item.name ?? 'Uncategorized',
    value: item.amountMinor,
    fill: item.color ?? palette.colors[index % palette.colors.length],
  }))

  return (
    <Card>
      <CardHeader
        title="Category breakdown"
        description={`Share of ${type === 'EXPENSE' ? 'spending' : 'income'}`}
        actions={
          <div className="flex items-center gap-2">
            <Select
              aria-label="Type"
              value={type}
              onChange={(e) => setType(e.target.value as 'EXPENSE' | 'INCOME')}
              className="h-9 w-28"
            >
              <option value="EXPENSE">Spending</option>
              <option value="INCOME">Income</option>
            </Select>
            <TrendRangeSelect value={selection} onChange={setSelection} />
          </div>
        }
      />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No transactions in this range.</p>}
        {!isLoading && !isEmpty && (
          <div className="flex flex-col items-center gap-6 lg:flex-row lg:items-start">
            <div
              role="img"
              aria-label={`Category breakdown of ${type.toLowerCase()}`}
              className="h-56 w-56 shrink-0"
            >
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Tooltip
                    contentStyle={tooltipStyle(palette)}
                    formatter={(value: unknown) => formatMoney(Number(value), currency)}
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
            <ul className="min-w-0 flex-1 space-y-1.5">
              {items.map((item, index) => (
                <li key={item.categoryId ?? 'uncategorized'} className="flex items-center gap-3 text-sm">
                  <span
                    aria-hidden
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ background: item.color ?? palette.colors[index % palette.colors.length] }}
                  />
                  <span className="min-w-0 flex-1 truncate text-text">
                    {item.name ?? 'Uncategorized'}
                  </span>
                  <span className="text-muted">
                    {formatMoney(item.amountMinor, currency)}
                  </span>
                  <span className="w-12 text-right font-medium text-text num">
                    {item.sharePct.toFixed(1)}%
                  </span>
                </li>
              ))}
              {items.length > SLICES && (
                <li className="text-xs text-subtle">and {items.length - SLICES} more…</li>
              )}
            </ul>
          </div>
        )}
      </CardBody>
    </Card>
  )
}