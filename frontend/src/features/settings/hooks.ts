import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/features/auth/auth.store'
import { useLogout } from '@/features/auth/hooks'
import type { SessionUser } from '@/features/auth/types'
import { settingsApi } from './api'
import type {
  ChangeBaseCurrencyInput,
  ChangePasswordInput,
  DeleteAccountInput,
  UpdateProfileInput,
  UserDataExport,
  UserProfile,
  UserPreferences,
} from './types'

export const settingsKeys = {
  profile: ['settings', 'profile'] as const,
  preferences: ['settings', 'preferences'] as const,
  currencies: ['reference', 'currencies'] as const,
  sessions: ['settings', 'sessions'] as const,
  audit: (page: number, size: number) => ['settings', 'audit', page, size] as const,
}

/** The header shows `SessionUser`, so profile edits have to be mirrored into the store. */
export function toSessionUser(profile: UserProfile): SessionUser {
  return {
    id: String(profile.id),
    email: profile.email,
    displayName: profile.displayName ?? profile.email,
    baseCurrency: profile.baseCurrencyCode,
    timezone: profile.timezone ?? undefined,
    locale: profile.locale ?? undefined,
    createdAt: profile.createdAt,
  }
}

export function useProfile() {
  return useQuery({
    queryKey: settingsKeys.profile,
    queryFn: settingsApi.getProfile,
    staleTime: 60_000,
  })
}

export function usePreferences() {
  return useQuery({
    queryKey: settingsKeys.preferences,
    queryFn: settingsApi.getPreferences,
    staleTime: 60_000,
  })
}

export function useCurrencies() {
  return useQuery({
    queryKey: settingsKeys.currencies,
    queryFn: settingsApi.listCurrencies,
    staleTime: 300_000,
  })
}

function useProfileSync(): (profile: UserProfile) => void {
  const status = useAuthStore((s) => s.status)
  const setUser = useAuthStore((s) => s.setUser)
  return (profile) => {
    if (status === 'authenticated') setUser(toSessionUser(profile))
  }
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  const sync = useProfileSync()

  return useMutation({
    mutationFn: (input: UpdateProfileInput) => settingsApi.updateProfile(input),
    onSuccess: (profile) => {
      queryClient.setQueryData(settingsKeys.profile, profile)
      sync(profile)
    },
  })
}

export function useChangeBaseCurrency() {
  const queryClient = useQueryClient()
  const sync = useProfileSync()

  return useMutation({
    mutationFn: (input: ChangeBaseCurrencyInput) => settingsApi.changeBaseCurrency(input),
    onSuccess: (profile) => {
      queryClient.setQueryData(settingsKeys.profile, profile)
      sync(profile)
      // Responses that embed the old base currency (accounts, dashboard,
      // analytics breakdowns) must refetch to relabel their figures.
      for (const root of ['accounts', 'analytics', 'dashboard'] as const) {
        void queryClient.invalidateQueries({ queryKey: [root] })
      }
    },
  })
}

export function useSavePreferences() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: UserPreferences) => settingsApi.savePreferences(input),
    onSuccess: (preferences) => {
      queryClient.setQueryData(settingsKeys.preferences, preferences)
    },
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (input: ChangePasswordInput) => settingsApi.changePassword(input),
  })
}

export function useSessions() {
  return useQuery({
    queryKey: settingsKeys.sessions,
    queryFn: () => settingsApi.listSessions(),
    staleTime: 30_000,
  })
}

export function useRevokeSession() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => settingsApi.revokeSession(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: settingsKeys.sessions })
    },
  })
}

export function useLogoutEverywhere() {
  const logout = useLogout()

  return useMutation({
    mutationFn: () => settingsApi.logoutAll(),
    onSuccess: () => void logout(),
    onError: () => void logout(),
  })
}

export function useExportData() {
  return useMutation<UserDataExport, Error, void>({
    mutationFn: () => settingsApi.exportData(),
  })
}

export function useDeleteAccount() {
  const logout = useLogout()

  return useMutation({
    mutationFn: (input: DeleteAccountInput) => settingsApi.deleteAccount(input),
    onSuccess: () => void logout(),
  })
}

export function useAuditLogs(page: number, size: number) {
  return useQuery({
    queryKey: settingsKeys.audit(page, size),
    queryFn: () => settingsApi.listAuditLogs(page, size),
    staleTime: 30_000,
  })
}
