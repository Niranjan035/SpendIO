import { forwardRef, useId, type SelectHTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
  hint?: string
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, hint, className, id, children, ...rest },
  ref,
) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const describedBy = error ? `${selectId}-error` : hint ? `${selectId}-hint` : undefined

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={selectId} className="text-sm font-medium text-text">
          {label}
        </label>
      )}
      <select
        ref={ref}
        id={selectId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          'h-10 w-full rounded-md border bg-surface px-3 text-sm text-text',
          'placeholder:text-subtle focus:border-primary focus:outline-none',
          'disabled:cursor-not-allowed disabled:bg-surface-sunken disabled:text-subtle',
          error ? 'border-danger' : 'border-border-strong',
          className,
        )}
        {...rest}
      >
        {children}
      </select>
      {hint && !error && (
        <p id={`${selectId}-hint`} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${selectId}-error`} role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
})
