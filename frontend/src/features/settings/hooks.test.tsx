import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAuthStore } from '@/features/auth/auth.store'
import { settingsApi } from './api'
import {
  settingsKeys,
  toSessionUser,
  useAuditLogs,
  useChangeBaseCurrency,
  useChangePassword,
  useDeleteAccount,
  useExportData,
  useProfile,
  useRevokeSession,
  useSavePreferences,
  useUpdateProfile,
} from './hooks'
import type { AuditLogEntry, UserProfile, UserPreferences } from './types'

vi.mock('./api', () => ({
  settingsApi: {
    getProfile: vi.fn(),
    updateProfile: vi.fn(),
    changeBaseCurrency: vi.fn(),
    getPreferences: vi.fn(),
    savePreferences: vi.fn(),
    listCurrencies: vi.fn(),
    changePassword: vi.fn(),
    listSessions: vi.fn(),
    revokeSession: vi.fn(),
    logoutAll: vi.fn(),
    exportData: vi.fn(),
    deleteAccount: vi.fn(),
    listAuditLogs: vi.fn(),
  },
}))

const profile: UserProfile = {
  id: 42,
  email: 'ana@example.com',
  firstName: 'Ana',
  lastName: 'Lopes',
  displayName: 'Ana Lopes',
  timezone: 'Europe/Lisbon',
  locale: 'en-GB',
  baseCurrencyCode: 'EUR',
  emailVerified: true,
  createdAt: '2026-01-15T10:00:00Z',
}

const preferences: UserPreferences = {
  theme: 'SYSTEM',
  dateFormat: 'yyyy-MM-dd',
  firstDayOfWeek: 1,
  decimalStyle: 'DOT',
  groupSeparator: ',',
  currencySymbolPosition: 'BEFORE',
  numberFormat: '#,##0.00',
  budgetAlertThresholdPct: 80,
  goalReminderDays: 30,
  recurringReminderDays: 3,
  emailDigestEnabled: false,
  reducedMotion: false,
}

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  return { queryClient, wrapper }
}

beforeEach(() => {
  useAuthStore.setState({ status: 'anonymous', user: null, hydrated: true })
})

describe('toSessionUser', () => {
  it('maps the profile onto the header shape', () => {
    expect(toSessionUser(profile)).toEqual({
      id: '42',
      email: 'ana@example.com',
      displayName: 'Ana Lopes',
      baseCurrency: 'EUR',
      timezone: 'Europe/Lisbon',
      locale: 'en-GB',
      createdAt: '2026-01-15T10:00:00Z',
    })
  })

  it('falls back to the email when there is no display name', () => {
    const mapped = toSessionUser({ ...profile, displayName: null, timezone: null, locale: null })
    expect(mapped.displayName).toBe('ana@example.com')
    expect(mapped.timezone).toBeUndefined()
    expect(mapped.locale).toBeUndefined()
  })
})

describe('useProfile', () => {
  it('loads the profile into the cache', async () => {
    vi.mocked(settingsApi.getProfile).mockResolvedValue(profile)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useProfile(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(profile)
    expect(queryClient.getQueryData(settingsKeys.profile)).toEqual(profile)
  })
})

describe('useUpdateProfile', () => {
  it('caches the patched profile and syncs the store when authenticated', async () => {
    const updated = { ...profile, firstName: 'Ana Maria', displayName: 'Ana Maria Lopes' }
    vi.mocked(settingsApi.updateProfile).mockResolvedValue(updated)
    useAuthStore.setState({
      status: 'authenticated',
      user: { id: '42', email: profile.email, displayName: 'Ana Lopes', baseCurrency: 'EUR' },
      hydrated: true,
    })
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateProfile(), { wrapper })
    act(() => result.current.mutate({ firstName: 'Ana Maria' }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(settingsApi.updateProfile).toHaveBeenCalledWith({ firstName: 'Ana Maria' })
    expect(queryClient.getQueryData(settingsKeys.profile)).toEqual(updated)
    expect(useAuthStore.getState().user?.displayName).toBe('Ana Maria Lopes')
  })

  it('leaves an anonymous store alone', async () => {
    vi.mocked(settingsApi.updateProfile).mockResolvedValue(profile)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateProfile(), { wrapper })
    act(() => result.current.mutate({ locale: 'pt-PT' }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(useAuthStore.getState().user).toBeNull()
  })
})

describe('useChangeBaseCurrency', () => {
  it('stores the new base currency and updates the store', async () => {
    const updated = { ...profile, baseCurrencyCode: 'USD' }
    vi.mocked(settingsApi.changeBaseCurrency).mockResolvedValue(updated)
    useAuthStore.setState({
      status: 'authenticated',
      user: { id: '42', email: profile.email, displayName: 'Ana Lopes', baseCurrency: 'EUR' },
      hydrated: true,
    })
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useChangeBaseCurrency(), { wrapper })
    act(() => result.current.mutate({ currencyCode: 'USD' }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(settingsApi.changeBaseCurrency).toHaveBeenCalledWith({ currencyCode: 'USD' })
    expect(queryClient.getQueryData(settingsKeys.profile)).toEqual(updated)
    expect(useAuthStore.getState().user?.baseCurrency).toBe('USD')
  })
})

describe('useSavePreferences', () => {
  it('caches the saved preferences', async () => {
    vi.mocked(settingsApi.savePreferences).mockResolvedValue(preferences)
    const { queryClient, wrapper } = createWrapper()

    const { result } = renderHook(() => useSavePreferences(), { wrapper })
    act(() => result.current.mutate(preferences))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(queryClient.getQueryData(settingsKeys.preferences)).toEqual(preferences)
  })
})

describe('useChangePassword', () => {
  it('posts the current and new password', async () => {
    vi.mocked(settingsApi.changePassword).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useChangePassword(), { wrapper })
    act(() => result.current.mutate({ currentPassword: 'old', newPassword: 'newsecret' }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(settingsApi.changePassword).toHaveBeenCalledWith({
      currentPassword: 'old',
      newPassword: 'newsecret',
    })
  })
})

describe('useRevokeSession', () => {
  it('revokes and refreshes the session list', async () => {
    vi.mocked(settingsApi.revokeSession).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useRevokeSession(), { wrapper })
    act(() => result.current.mutate('abc'))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(settingsApi.revokeSession).toHaveBeenCalledWith('abc')
    expect(invalidate).toHaveBeenCalledWith({ queryKey: settingsKeys.sessions })
  })
})

describe('useExportData', () => {
  it('returns the export payload', async () => {
    vi.mocked(settingsApi.exportData).mockResolvedValue({
      exportedAt: '2026-02-01T00:00:00Z',
      data: { profile: { email: 'ana@example.com' } },
    })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useExportData(), { wrapper })
    act(() => result.current.mutate())

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.data).toEqual({ profile: { email: 'ana@example.com' } })
  })
})

describe('useDeleteAccount', () => {
  it('sends the confirmation and clears the local session', async () => {
    vi.mocked(settingsApi.deleteAccount).mockResolvedValue(undefined)
    useAuthStore.setState({
      status: 'authenticated',
      user: { id: '42', email: profile.email, displayName: 'Ana Lopes', baseCurrency: 'EUR' },
      hydrated: true,
    })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useDeleteAccount(), { wrapper })
    act(() => result.current.mutate({ password: 'p', confirmationText: 'DELETE' }))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(settingsApi.deleteAccount).toHaveBeenCalledWith({
      password: 'p',
      confirmationText: 'DELETE',
    })
    await waitFor(() => expect(useAuthStore.getState().status).toBe('anonymous'))
  })
})

describe('useAuditLogs', () => {
  it('loads a page of the audit trail', async () => {
    const entry: AuditLogEntry = {
      id: 1,
      actorType: 'USER',
      action: 'PASSWORD_CHANGED',
      entityType: null,
      entityId: null,
      ipAddress: '127.0.0.1',
      traceId: null,
      before: null,
      after: null,
      createdAt: '2026-02-01T09:00:00Z',
    }
    vi.mocked(settingsApi.listAuditLogs).mockResolvedValue({
      content: [entry],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useAuditLogs(0, 20), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(settingsApi.listAuditLogs).toHaveBeenCalledWith(0, 20)
    expect(result.current.data?.content).toEqual([entry])
  })
})
