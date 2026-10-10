import { http } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  AuditLogPage,
  ChangeBaseCurrencyInput,
  ChangePasswordInput,
  Currency,
  DeleteAccountInput,
  UpdateProfileInput,
  UserDataExport,
  UserPreferences,
  UserProfile,
  UserSession,
} from './types'

export const settingsApi = {
  getProfile: () => http.get<UserProfile>(endpoints.profile.me),

  updateProfile: (input: UpdateProfileInput) =>
    http.patch<UserProfile>(endpoints.profile.me, input),

  changeBaseCurrency: (input: ChangeBaseCurrencyInput) =>
    http.put<UserProfile>(endpoints.profile.baseCurrency, input),

  getPreferences: () => http.get<UserPreferences>(endpoints.profile.preferences),

  savePreferences: (input: UserPreferences) =>
    http.put<UserPreferences>(endpoints.profile.preferences, input),

  listCurrencies: () => http.get<Currency[]>(endpoints.reference.currencies),

  changePassword: (input: ChangePasswordInput) =>
    http.post<void>(endpoints.auth.changePassword, input, { requireCsrf: true }),

  listSessions: () => http.get<UserSession[]>(endpoints.auth.sessions),

  revokeSession: (id: string) =>
    http.delete<void>(endpoints.auth.revokeSession(id), { requireCsrf: true }),

  logoutAll: () => http.post<void>(endpoints.auth.logoutAll, undefined, { requireCsrf: true }),

  exportData: () => http.post<UserDataExport>(endpoints.profile.export),

  deleteAccount: (input: DeleteAccountInput) =>
    http.delete<void>(endpoints.profile.delete, { body: input, requireCsrf: true }),

  listAuditLogs: (page: number, size: number) =>
    http.get<AuditLogPage>(endpoints.audit.list, { query: { page, size } }),
}
