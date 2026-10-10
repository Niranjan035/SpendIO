import { useCallback } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi, type LoginRequest, type RegisterRequest, type SessionResponse } from './api'
import { useAuthStore } from './auth.store'
import { clearAccessToken, setAccessToken } from '@/services/http/tokenManager'

function adoptSession(
  session: SessionResponse,
  setAuthenticated: (user: SessionResponse['user']) => void,
): void {
  setAccessToken(session.accessToken, Date.now() + session.expiresIn * 1000)
  setAuthenticated(session.user)
}

export function useLogin() {
  const setAuthenticated = useAuthStore((s) => s.setAuthenticated)
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: LoginRequest) => authApi.login(input),
    onSuccess: (session) => {
      adoptSession(session, setAuthenticated)
      void queryClient.invalidateQueries()
    },
  })
}

export function useRegister() {
  const setAuthenticated = useAuthStore((s) => s.setAuthenticated)
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: RegisterRequest) => authApi.register(input),
    onSuccess: (session) => {
      adoptSession(session, setAuthenticated)
      void queryClient.invalidateQueries()
    },
  })
}

export function useLogout() {
  const setAnonymous = useAuthStore((s) => s.setAnonymous)
  const queryClient = useQueryClient()

  return useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // Logging out locally must succeed even when the call fails.
    }
    clearAccessToken()
    setAnonymous()
    queryClient.clear()
    if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel('spendio-auth')
      channel.postMessage({ type: 'logout' })
      channel.close()
    }
  }, [queryClient, setAnonymous])
}

/** Revoke every session server-side, then run the local teardown. */
export function useLogoutAll() {
  const logout = useLogout()

  return useMutation({
    mutationFn: () => authApi.logoutAll(),
    onSuccess: () => void logout(),
    onError: () => void logout(),
  })
}

export function useForgotPassword() {
  return useMutation({ mutationFn: (email: string) => authApi.forgotPassword(email) })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (input: { token: string; password: string }) =>
      authApi.resetPassword(input.token, input.password),
  })
}

export function useSession() {
  const status = useAuthStore((s) => s.status)
  const user = useAuthStore((s) => s.user)
  const hydrated = useAuthStore((s) => s.hydrated)
  return { status, user, hydrated }
}

export function useCurrentUser() {
  const isAuthenticated = useAuthStore((s) => s.status === 'authenticated')
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.me(),
    enabled: isAuthenticated,
    staleTime: 60_000,
  })
}
