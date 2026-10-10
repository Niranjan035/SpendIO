import { cn } from '@/lib/cn'

const STEPS = [
  { key: 'upload', label: 'Upload' },
  { key: 'map', label: 'Map columns' },
  { key: 'review', label: 'Review' },
  { key: 'done', label: 'Done' },
] as const

export type ImportStepKey = (typeof STEPS)[number]['key']

export function ImportStepper({ current }: { current: ImportStepKey }) {
  const activeIndex = STEPS.findIndex((step) => step.key === current)

  return (
    <ol aria-label="Import progress" className="flex flex-wrap gap-2">
      {STEPS.map((step, index) => {
        const done = index < activeIndex
        const active = index === activeIndex
        return (
          <li
            key={step.key}
            aria-current={active ? 'step' : undefined}
            className={cn(
              'flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium',
              active
                ? 'border-primary bg-primary-subtle text-primary'
                : done
                  ? 'border-success/40 bg-success-subtle text-success'
                  : 'border-border bg-surface text-muted',
            )}
          >
            <span aria-hidden="true">{done ? '✓' : index + 1}</span>
            {step.label}
          </li>
        )
      })}
    </ol>
  )
}
