import { useState } from 'react'
import { Select } from '@/components/ui/Select'
import { GRANULARITY_OPTIONS, RANGE_PRESETS, presetRange } from '../labels'
import type { AnalyticsGranularity } from '../types'

export interface TrendSelection {
  from: string
  to: string
  granularity: AnalyticsGranularity
}

export interface TrendRangeSelectProps {
  value: TrendSelection
  onChange: (next: TrendSelection) => void
  className?: string
}

/**
 * Shared range controls for the trend charts (D.14): a preset picker plus an
 * explicit granularity override, both safely inside the backend span caps.
 */
export function TrendRangeSelect({ value, onChange, className }: TrendRangeSelectProps) {
  const [days, setDays] = useState(30)

  function applyPreset(nextDays: number) {
    setDays(nextDays)
    const next = RANGE_PRESETS.find((p) => p.days === nextDays) ?? RANGE_PRESETS[0]!
    onChange({ ...presetRange(next), granularity: value.granularity })
  }

  function applyGranularity(granularity: AnalyticsGranularity) {
    onChange({ ...value, granularity })
  }

  return (
    <div className={`flex items-center gap-2 ${className ?? ''}`}>
      <Select
        aria-label="Range"
        value={days}
        onChange={(e) => applyPreset(Number(e.target.value))}
        className="h-9 w-32"
      >
        {RANGE_PRESETS.map((option) => (
          <option key={option.days} value={option.days}>
            {option.label}
          </option>
        ))}
      </Select>
      <Select
        aria-label="Granularity"
        value={value.granularity}
        onChange={(e) => applyGranularity(e.target.value as AnalyticsGranularity)}
        className="h-9 w-28"
      >
        {GRANULARITY_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </Select>
    </div>
  )
}