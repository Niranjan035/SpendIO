import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '@/features/auth/auth.store'
import { paths } from './paths'
import { FullPageLoader } from '@/components/feedback/LoadingSpinner'

/** Sends already-authenticated users away from login/register/reset screens. */
export function GuestOnlyRoute({ children }: { children: ReactNode }) {
  const status = useAuthStore((s) => s.status)
  const hydrated = useAuthStore((s) => s.hydrated)
  const location = useLocation()

  if (!hydrated || status === 'unknown') return <FullPageLoader label="Restoring session…" />
  if (status === 'authenticated') {
    const intended = (location.state as { from?: string } | null)?.from
    return (
      <Navigate to={intended && intended.startsWith('/') ? intended : paths.dashboard} replace />
    )
  }
  return <>{children}</>
}
