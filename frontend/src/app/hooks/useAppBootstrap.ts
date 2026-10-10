import { useEffect } from 'react'
import { useAuthStore } from '@/features/auth/auth.store'
import { clearAccessToken } from '@/services/http/tokenManager'
import { refreshSession } from '@/services/http/apiClient'
import { authApi } from '@/features/auth/api'

/**
 * Resumes a session on page reload (refresh cookie -> access token -> profile)
 * and keeps tabs in sync: a logout anywhere logs out everywhere.
 */
export function useAppBootstrap(): { ready: boolean } {
  const hydrated = useAuthStore((s) => s.hydrated)
  const setAuthenticated = useAuthStore((s) => s.setAuthenticated)
  const setAnonymous = useAuthStore((s) => s.setAnonymous)
  const finishHydration = useAuthStore((s) => s.finishHydration)

  useEffect(() => {
    let cancelled = false

    void (async () => {
      const refreshed = await refreshSession()
      if (cancelled) return
      if (refreshed) {
        try {
          const user = await authApi.me()
          if (!cancelled) setAuthenticated(user)
        } catch {
          if (!cancelled) setAnonymous()
        }
      } else if (!cancelled) {
        setAnonymous()
      }
      if (!cancelled) finishHydration()
    })()

    const onExpired = () => {
      clearAccessToken()
      setAnonymous()
    }
    window.addEventListener('spendio:session-expired', onExpired)

    const channel =
      typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('spendio-auth') : null
    channel?.addEventListener('message', (event: MessageEvent) => {
      if ((event.data as { type?: string })?.type === 'logout') {
        clearAccessToken()
        setAnonymous()
      }
    })

    return () => {
      cancelled = true
      window.removeEventListener('spendio:session-expired', onExpired)
      channel?.close()
    }
  }, [finishHydration, setAnonymous, setAuthenticated])

  return { ready: hydrated }
}
