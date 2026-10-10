import { z } from 'zod'

export const passwordSchema = z
  .string()
  .min(10, 'Use at least 10 characters')
  .max(72, 'Too long')
  .refine((value) => /[a-z]/.test(value), 'Include a lowercase letter')
  .refine((value) => /[A-Z]/.test(value), 'Include an uppercase letter')
  .refine((value) => /\d/.test(value), 'Include a digit')

export const emailSchema = z
  .string()
  .trim()
  .min(1, 'Email is required')
  .email('Enter a valid email address')
  .max(254, 'Email is too long')

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'Password is required'),
})

export const registerSchema = z.object({
  email: emailSchema,
  password: passwordSchema,
  firstName: z.string().trim().min(1, 'First name is required').max(80, 'Name is too long'),
  lastName: z.string().trim().min(1, 'Last name is required').max(80, 'Name is too long'),
  timezone: z.string().min(1).max(64),
  locale: z.string().min(1).max(16),
  baseCurrencyCode: z.string().length(3),
})

/** Browser defaults for the register form's hidden locale fields. */
export function browserDefaults(): { timezone: string; locale: string } {
  const timezone =
    typeof Intl !== 'undefined' ? Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC' : 'UTC'
  const locale =
    typeof navigator !== 'undefined' && navigator.language ? navigator.language : 'en-GB'
  return { timezone, locale: locale.slice(0, 16) }
}

export const forgotPasswordSchema = z.object({ email: emailSchema })

export const resetPasswordSchema = z
  .object({
    token: z.string().min(1, 'Reset token is required'),
    password: passwordSchema,
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  })

export type LoginFormValues = z.infer<typeof loginSchema>
export type RegisterFormValues = z.infer<typeof registerSchema>
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>

/** Coarse strength meter used by the register/reset forms. */
export function passwordStrength(password: string): { score: 0 | 1 | 2 | 3 | 4; label: string } {
  let score = 0
  if (password.length >= 10) score += 1
  if (password.length >= 14) score += 1
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1
  if (/\d/.test(password) && /[^A-Za-z0-9]/.test(password)) score += 1
  const clamped = Math.min(4, score) as 0 | 1 | 2 | 3 | 4
  const labels = ['Too weak', 'Weak', 'Fair', 'Good', 'Strong'] as const
  return { score: clamped, label: labels[clamped] }
}

/** Human copy for the backend {@code PASSWORD_POLICY_VIOLATION} rule ids (D.2). */
export function describePolicyViolation(id: string): string {
  const atLeast = /^at-least-(\d+)-characters$/.exec(id)
  if (atLeast) return `Use at least ${atLeast[1]} characters`
  const atMost = /^at-most-(\d+)-characters$/.exec(id)
  if (atMost) return `Use at most ${atMost[1]} characters`
  switch (id) {
    case 'a-lowercase-letter':
      return 'Include a lowercase letter'
    case 'an-uppercase-letter':
      return 'Include an uppercase letter'
    case 'a-digit':
      return 'Include a digit'
    case 'not-your-email-address':
      return 'Password cannot be your email address'
    case 'not-a-known-breached-password':
      return 'That password appears in known data breaches — choose another'
    default:
      return 'Password does not meet the policy'
  }
}
