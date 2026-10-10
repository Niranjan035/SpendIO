import { useState } from 'react'
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatMoney } from '@/lib/money'
import { compactMoney, bucketLabel, monthBucketLabel, presetRange, RANGE_PRESETS } from '../labels'
import { useSpendingTrend } from '../hooks'
import { tooltipStyle, useChartPalette } from './chartPalette'
import { TrendRangeSelect, type TrendSelection } from './TrendRangeSelect'

export interface SpendingTrendCardProps {
  currency: string
}

export function SpendingTrendCard({ currency }: SpendingTrendCardProps) {
  const [selection, setSelection] = useState<TrendSelection>(() => {
    const preset = RANGE_PRESETS[0]!
    return { ...presetRange(preset), granularity: preset.granularity }
  })
  const { data, isLoading, isError } = useSpendingTrend(selection)
  const palette = useChartPalette()

  const points = data ?? []
  const isEmpty = !isLoading && !isError && points.length === 0

  return (
    <Card>
      <CardHeader
        title="Spending trend"
        description="Expense per bucket against the prorated budget line"
        actions={<TrendRangeSelect value={selection} onChange={setSelection} />}
      />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No spending in this range.</p>}
        {!isLoading && !isEmpty && (
          <div role="img" aria-label="Spending trend chart" className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={points} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid stroke={palette.grid} strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="period"
                  tick={{ fill: palette.axis, fontSize: 11 }}
                  tickLine={false}
                  axisLine={{ stroke: palette.grid }}
                  tickFormatter={monthBucketLabel}
                />
                <YAxis
                  tick={{ fill: palette.axis, fontSize: 11 }}
                  tickLine={false}
                  axisLine={false}
                  tickFormatter={(value) => compactMoney(Number(value), currency)}
                  width={48}
                />
                <Tooltip
                  contentStyle={tooltipStyle(palette)}
                  labelFormatter={bucketLabel}
                  formatter={(value) => formatMoney(Number(value), currency)}
                />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Area
                  dataKey="expenseMinor"
                  name="Spending"
                  stroke={palette.expense}
                  fill={palette.expense}
                  fillOpacity={0.25}
                />
                <Line
                  dataKey="budgetMinor"
                  name="Budget"
                  stroke={palette.budget}
                  strokeWidth={2}
                  strokeDasharray="5 4"
                  dot={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardBody>
    </Card>
  )
}