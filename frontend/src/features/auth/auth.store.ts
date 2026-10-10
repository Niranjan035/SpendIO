import { create } from 'zustand'
import type { SessionUser } from './api'

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous'

interface AuthState {
  status: AuthStatus
  user: SessionUser | null
  /** Set once the bootstrap refresh attempt finished, whatever the outcome. */
  hydrated: boolean
  setAuthenticated: (user: SessionUser) => void
  setUser: (user: SessionUser) => void
  setAnonymous: () => void
  finishHydration: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'unknown',
  user: null,
  hydrated: false,
  setAuthenticated: (user) => set({ status: 'authenticated', user, hydrated: true }),
  setUser: (user) => set({ user }),
  setAnonymous: () => set({ status: 'anonymous', user: null, hydrated: true }),
  finishHydration: () => set({ hydrated: true }),
}))

export const selectIsAuthenticated = (state: AuthState): boolean => state.status === 'authenticated'
