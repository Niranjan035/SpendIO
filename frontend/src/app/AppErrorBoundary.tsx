import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from '@/components/ui/Button'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

/** Catches render-time failures anywhere in the tree and offers a way out. */
export class AppErrorBoundary extends Component<Props, State> {
  override state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  override componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('Unhandled UI error', error, info.componentStack)
  }

  private reset = (): void => {
    this.setState({ error: null })
  }

  override render(): ReactNode {
    const { error } = this.state
    if (!error) return this.props.children

    return (
      <main className="flex min-h-screen items-center justify-center bg-bg px-6">
        <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-2">
          <h1 className="text-lg font-semibold text-text">Something went wrong</h1>
          <p className="mt-2 text-sm text-muted">
            The screen could not be rendered. Your data is unaffected — try again, or reload the
            application.
          </p>
          <pre className="mt-4 max-h-40 overflow-auto rounded-md bg-surface-sunken p-3 text-xs text-muted">
            {error.message}
          </pre>
          <div className="mt-5 flex gap-2">
            <Button onClick={this.reset}>Try again</Button>
            <Button variant="secondary" onClick={() => window.location.reload()}>
              Reload
            </Button>
          </div>
        </div>
      </main>
    )
  }
}
