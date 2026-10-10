/**
 * Access token lives **in memory only** (ADR-0003). The refresh token is an
 * httpOnly cookie the browser sends; nothing here can read it.
 */
let accessToken: string | null = null
let expiresAtMs = 0

export function setAccessToken(token: string | null, expiresAt = 0): void {
  accessToken = token
  expiresAtMs = expiresAt
}

export function getAccessToken(): string | null {
  if (accessToken && expiresAtMs > 0 && Date.now() >= expiresAtMs) {
    accessToken = null
    expiresAtMs = 0
  }
  return accessToken
}

export function hasAccessToken(): boolean {
  return getAccessToken() !== null
}

export function clearAccessToken(): void {
  accessToken = null
  expiresAtMs = 0
}
