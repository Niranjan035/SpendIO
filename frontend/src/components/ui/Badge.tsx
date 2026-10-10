import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'primary'

const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-surface-sunken text-muted border-border',
  info: 'bg-info-subtle text-info border-info/25',
  success: 'bg-success-subtle text-success border-success/25',
  warning: 'bg-warning-subtle text-warning border-warning/25',
  danger: 'bg-danger-subtle text-danger border-danger/25',
  primary: 'bg-primary-subtle text-primary border-primary/25',
}

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone
}

export function Badge({ tone = 'neutral', className, ...rest }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium',
        TONES[tone],
        className,
      )}
      {...rest}
    />
  )
}
