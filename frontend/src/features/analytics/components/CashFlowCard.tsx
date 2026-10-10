import { useState } from 'react'
import {
  Bar,
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
import { useCashFlow } from '../hooks'
import { tooltipStyle, useChartPalette } from './chartPalette'
import { TrendRangeSelect, type TrendSelection } from './TrendRangeSelect'

export interface CashFlowCardProps {
  currency: string
}

export function CashFlowCard({ currency }: CashFlowCardProps) {
  const [selection, setSelection] = useState<TrendSelection>(() => {
    const preset = RANGE_PRESETS[2]!
    return { ...presetRange(preset), granularity: preset.granularity }
  })
  const { data, isLoading, isError } = useCashFlow(selection)
  const palette = useChartPalette()

  const points = data ?? []
  const isEmpty = !isLoading && !isError && points.length === 0

  return (
    <Card>
      <CardHeader
        title="Cash flow"
        description="Income, spending and net per bucket"
        actions={<TrendRangeSelect value={selection} onChange={setSelection} />}
      />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No transactions in this range.</p>}
        {!isLoading && !isEmpty && (
          <div role="img" aria-label="Cash flow chart" className="h-72">
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
                <Bar dataKey="incomeMinor" name="Income" fill={palette.income} radius={[2, 2, 0, 0]} />
                <Bar dataKey="expenseMinor" name="Spending" fill={palette.expense} radius={[2, 2, 0, 0]} />
                <Line
                  dataKey="netMinor"
                  name="Net"
                  stroke={palette.net}
                  strokeWidth={2}
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