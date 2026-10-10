import { ApiError } from '@/services/http/ApiError'
import { describePolicyViolation } from './schemas'

/** The backend {@code violations} property of a PASSWORD_POLICY_VIOLATION, if any. */
export function policyViolations(error: unknown): string[] {
  if (!(error instanceof ApiError)) return []
  const violations = error.payload?.violations
  return Array.isArray(violations) ? violations.map(String) : []
}

/** Humanised password-policy message, or {@code null} when the error is not one. */
export function policyMessage(error: unknown): string | null {
  const violations = policyViolations(error)
  return violations.length > 0 ? violations.map(describePolicyViolation).join(' ') : null
}
