import { cn } from '@/lib/cn'

export function LoadingSpinner({ label = 'Loading' }: { label?: string }) {
  return (
    <span
      role="status"
      aria-label={label}
      className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-border-strong border-t-primary"
    />
  )
}

export function FullPageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-bg">
      <div className="flex flex-col items-center gap-3">
        <LoadingSpinner label={label} />
        <p className="text-sm text-muted" aria-live="polite">
          {label}
        </p>
      </div>
    </div>
  )
}

export function InlineError({
  message,
  onRetry,
  className,
}: {
  message: string
  onRetry?: () => void
  className?: string
}) {
  return (
    <div
      role="alert"
      className={cn(
        'flex items-center justify-between gap-3 rounded-md border border-danger/30 bg-danger-subtle px-3 py-2 text-sm text-danger',
        className,
      )}
    >
      <span>{message}</span>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="font-medium underline underline-offset-2 hover:no-underline"
        >
          Retry
        </button>
      )}
    </div>
  )
}
