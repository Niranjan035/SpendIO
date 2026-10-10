/** Session payloads shared by the auth API, store and route guards. */

export interface SessionResponse {
  accessToken: string
  expiresIn: number
  tokenType?: string
  user: SessionUser
}

export interface SessionUser {
  id: string
  email: string
  firstName?: string
  lastName?: string
  displayName: string
  baseCurrency: string
  timezone?: string
  locale?: string
  emailVerified?: boolean
  createdAt?: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  timezone: string
  locale: string
  baseCurrencyCode: string
}

export interface LoginRequest {
  email: string
  password: string
}

/** Raw backend shapes (ARCHITECTURE.md D.2/D.3) before session mapping. */
export interface BackendUser {
  id: number | string
  email: string
  firstName?: string | null
  lastName?: string | null
  displayName?: string | null
  timezone?: string | null
  locale?: string | null
  baseCurrencyCode: string
  emailVerified?: boolean
  createdAt?: string
}

export interface BackendAuthResponse {
  accessToken: string
  expiresIn: number
  tokenType?: string
  user: BackendUser
  permissions?: string[]
}
