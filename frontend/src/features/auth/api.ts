import { http } from '@/services/http/apiClient'
import { endpoints } from '@/services/http/endpoints'
import type {
  BackendAuthResponse,
  BackendUser,
  LoginRequest,
  RegisterRequest,
  SessionResponse,
  SessionUser,
} from './types'

export type { LoginRequest, RegisterRequest, SessionResponse, SessionUser } from './types'

/**
 * The API answers with {@code UserResponse} (numeric id, {@code baseCurrencyCode});
 * the store speaks {@link SessionUser}. One mapping, used by every auth call.
 */
export function toSessionUser(user: BackendUser): SessionUser {
  return {
    id: String(user.id),
    email: user.email,
    firstName: user.firstName ?? undefined,
    lastName: user.lastName ?? undefined,
    displayName: user.displayName?.trim() || user.email,
    baseCurrency: user.baseCurrencyCode,
    timezone: user.timezone ?? undefined,
    locale: user.locale ?? undefined,
    emailVerified: user.emailVerified,
    createdAt: user.createdAt,
  }
}

function toSession(response: BackendAuthResponse): SessionResponse {
  return {
    accessToken: response.accessToken,
    expiresIn: response.expiresIn,
    tokenType: response.tokenType ?? 'Bearer',
    user: toSessionUser(response.user),
  }
}

export const authApi = {
  register: (input: RegisterRequest) =>
    http
      .post<BackendAuthResponse>(endpoints.auth.register, input, { skipAuth: true })
      .then(toSession),

  login: (input: LoginRequest) =>
    http.post<BackendAuthResponse>(endpoints.auth.login, input, { skipAuth: true }).then(toSession),

  refresh: () =>
    http
      .post<BackendAuthResponse>(endpoints.auth.refresh, undefined, { skipAuth: true })
      .then(toSession),

  logout: () => http.post<void>(endpoints.auth.logout, undefined, { requireCsrf: true }),

  logoutAll: () => http.post<void>(endpoints.auth.logoutAll, undefined, { requireCsrf: true }),

  forgotPassword: (email: string) =>
    http.post<{ message: string }>(endpoints.auth.forgotPassword, { email }, { skipAuth: true }),

  resetPassword: (token: string, password: string) =>
    http.post<void>(endpoints.auth.resetPassword, { token, password }, { skipAuth: true }),

  verifyEmail: (token: string) =>
    http.post<{ message: string }>(endpoints.auth.verifyEmail, { token }),

  resendVerification: () =>
    http.post<{ message: string }>(endpoints.auth.resendVerification, undefined),

  listSessions: () => http.get<unknown[]>(endpoints.auth.sessions),

  revokeSession: (id: string) => http.delete<void>(endpoints.auth.revokeSession(id)),

  me: () => http.get<BackendUser>(endpoints.profile.me).then(toSessionUser),
}
