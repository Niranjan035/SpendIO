import { RouterProvider } from 'react-router-dom'
import { useAppBootstrap } from './hooks/useAppBootstrap'
import { AppErrorBoundary } from './AppErrorBoundary'
import { FullPageLoader } from '@/components/feedback/LoadingSpinner'
import { router } from './router'

export function App() {
  const { ready } = useAppBootstrap()

  return (
    <AppErrorBoundary>
      {ready ? <RouterProvider router={router} /> : <FullPageLoader label="Restoring session…" />}
    </AppErrorBoundary>
  )
}
