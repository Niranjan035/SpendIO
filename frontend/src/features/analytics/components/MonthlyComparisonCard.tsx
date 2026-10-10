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
import { Select } from '@/components/ui/Select'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { formatMoney } from '@/lib/money'
import { format } from 'date-fns'
import { compactMoney, bucketLabel } from '../labels'
import { useMonthlyComparison } from '../hooks'
import { tooltipStyle, useChartPalette } from './chartPalette'

export interface MonthlyComparisonCardProps {
  currency: string
}

export function MonthlyComparisonCard({ currency }: MonthlyComparisonCardProps) {
  const currentYear = new Date().getFullYear()
  const [year, setYear] = useState(currentYear)
  const { data, isLoading, isError } = useMonthlyComparison(year)
  const palette = useChartPalette()

  const points = data ?? []
  const isEmpty = !isLoading && !isError && points.length === 0
  const years = [currentYear, currentYear - 1, currentYear - 2]

  return (
    <Card>
      <CardHeader
        title="Monthly comparison"
        description={`Income, spending and net across ${year}`}
        actions={
          <Select
            aria-label="Year"
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="h-9 w-28"
          >
            {years.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </Select>
        }
      />
      <CardBody>
        {isLoading && <p className="text-sm text-muted">Loading…</p>}
        {isEmpty && <p className="text-sm text-muted">No transactions in {year}.</p>}
        {!isLoading && !isEmpty && (
          <div role="img" aria-label="Monthly comparison chart" className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={points} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid stroke={palette.grid} strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="month"
                  tick={{ fill: palette.axis, fontSize: 11 }}
                  tickLine={false}
                  axisLine={{ stroke: palette.grid }}
                  tickFormatter={(value: unknown) =>
                    format(new Date(`${String(value)}-01T00:00:00`), 'MMM')
                  }
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
                <Line dataKey="netMinor" name="Net" stroke={palette.net} strokeWidth={2} dot={false} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardBody>
    </Card>
  )
}