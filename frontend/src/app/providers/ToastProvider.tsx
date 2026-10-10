/* eslint-disable react-refresh/only-export-components -- the context hook must live with its provider */
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

export type ToastTone = 'info' | 'success' | 'warning' | 'error'

export interface Toast {
  id: string
  tone: ToastTone
  message: string
  /** Optional inline action, e.g. "Undo". */
  action?: { label: string; onClick: () => void }
}

interface ToastContextValue {
  toasts: Toast[]
  push: (toast: Omit<Toast, 'id'>) => string
  dismiss: (id: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const TONE_LIVE_REGION: Record<ToastTone, 'polite' | 'assertive'> = {
  info: 'polite',
  success: 'polite',
  warning: 'assertive',
  error: 'assertive',
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const push = useCallback(
    (toast: Omit<Toast, 'id'>) => {
      const id = crypto.randomUUID()
      setToasts((current) => [...current, { ...toast, id }])
      window.setTimeout(() => dismiss(id), toast.tone === 'error' ? 8000 : 5000)
      return id
    },
    [dismiss],
  )

  const value = useMemo(() => ({ toasts, push, dismiss }), [toasts, push, dismiss])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="fixed right-4 bottom-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            aria-live={TONE_LIVE_REGION[toast.tone]}
            className="flex items-start gap-3 rounded-md border border-border bg-surface-raised p-3 shadow-2"
          >
            <span
              className={
                'mt-0.5 h-2 w-2 shrink-0 rounded-full ' +
                (toast.tone === 'error'
                  ? 'bg-danger'
                  : toast.tone === 'success'
                    ? 'bg-success'
                    : toast.tone === 'warning'
                      ? 'bg-warning'
                      : 'bg-info')
              }
            />
            <div className="flex-1 text-sm text-text">{toast.message}</div>
            {toast.action && (
              <button
                type="button"
                className="text-sm font-medium text-primary hover:underline"
                onClick={() => {
                  toast.action?.onClick()
                  dismiss(toast.id)
                }}
              >
                {toast.action.label}
              </button>
            )}
            <button
              type="button"
              aria-label="Dismiss notification"
              className="text-subtle hover:text-text"
              onClick={() => dismiss(toast.id)}
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const value = useContext(ToastContext)
  if (!value) throw new Error('useToast must be used inside ToastProvider')
  return value
}
