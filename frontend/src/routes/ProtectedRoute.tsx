import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '@/features/auth/auth.store'
import { paths } from './paths'
import { FullPageLoader } from '@/components/feedback/LoadingSpinner'

/** Redirects anonymous visitors to /login, remembering where they wanted to go. */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const status = useAuthStore((s) => s.status)
  const hydrated = useAuthStore((s) => s.hydrated)
  const location = useLocation()

  if (!hydrated || status === 'unknown') return <FullPageLoader label="Restoring session…" />
  if (status !== 'authenticated') {
    return (
      <Navigate to={paths.login} replace state={{ from: location.pathname + location.search }} />
    )
  }
  return <>{children}</>
}
