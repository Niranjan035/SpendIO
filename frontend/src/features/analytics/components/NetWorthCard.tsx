import { useState } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatMoney } from '@/lib/money'
import { formatBusinessDate } from '@/lib/dates'
import { compactMoney, presetRange, RANGE_PRESETS } from '../labels'
import { useNetWorth } from '../hooks'
import { tooltipStyle, useChartPalette } from './chartPalette'
import { TrendRangeSelect, type TrendSelection } from './TrendRangeSelect'

function netWorthDate(value: unknown): string {
  return formatBusinessDate(typeof value === 'string' ? value : String(value))
}

export interface NetWorthCardProps {
  currency: string
}

export function NetWorthCard({ currency }: NetWorthCardProps) {
  const [selection, setSelection] = useState<TrendSelection>(() => {
    const preset = RANGE_PRESETS[2]!
    return { ...presetRange(preset), granularity: preset.granularity }
  })
  const { data, isLoading, isError } = useNetWorth(selection)
  const palette = useChartPalette()

  const points = data ?? []
  const isEmpty = !isLoading && !isError && points.length === 0

  return (
    <Card>
      <CardHeader
        title="Net worth"
        description="Snapshot totals across all accounts (base currency)"
        actions={<TrendRangeSelect value={selection} onChange={setSelection} />}
      />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No net worth history yet.</p>}
        {!isLoading && !isEmpty && (
          <div role="img" aria-label="Net worth chart" className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={points}>
                <defs>
                  <linearGradient id="netWorthFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={palette.income} stopOpacity={0.3} />
                    <stop offset="100%" stopColor={palette.income} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={palette.grid} strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="date"
                  tick={{ fill: palette.axis, fontSize: 11 }}
                  tickLine={false}
                  axisLine={{ stroke: palette.grid }}
                  tickFormatter={netWorthDate}
                  minTickGap={32}
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
                  labelFormatter={netWorthDate}
                  formatter={(value) => formatMoney(Number(value), currency)}
                />
                <Area
                  dataKey="totalMinor"
                  name="Net worth"
                  stroke={palette.income}
                  strokeWidth={2}
                  fill="url(#netWorthFill)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardBody>
    </Card>
  )
}