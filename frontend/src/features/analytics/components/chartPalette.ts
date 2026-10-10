import { useMemo } from 'react'
import { useTheme } from '@/app/providers/ThemeProvider'

/** Resolves the active theme's chart palette from the CSS tokens (tokens.css). */
export interface ChartPalette {
  axis: string
  grid: string
  tooltipBg: string
  tooltipText: string
  income: string
  expense: string
  net: string
  budget: string
  savings: string
  colors: string[]
}

function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

export function useChartPalette(): ChartPalette {
  const { resolved } = useTheme()
  return useMemo(() => {
    const colors = [1, 2, 3, 4, 5, 6, 7, 8].map((i) =>
      cssVar(`--chart-${i}`, i === 6 ? '#5b6472' : '#1f5eff'),
    )
    return {
      axis: cssVar('--c-text-subtle', '#7b8493'),
      grid: cssVar('--c-border', '#262b35'),
      tooltipBg: cssVar('--c-surface-raised', '#ffffff'),
      tooltipText: cssVar('--c-text', '#14161a'),
      income: colors[0]!,
      expense: colors[6]!,
      net: colors[7]!,
      budget: colors[2]!,
      savings: colors[1]!,
      colors,
    }
    // The palette is keyed to the resolved theme, so charts re-render on change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resolved])
}

/** Tooltip style object wired to the active theme. */
export function tooltipStyle(palette: ChartPalette): React.CSSProperties {
  return {
    background: palette.tooltipBg,
    border: `1px solid ${palette.grid}`,
    borderRadius: 8,
    color: palette.tooltipText,
    fontSize: 12,
  }
}