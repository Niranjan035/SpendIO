import { z } from 'zod'

const TZ_PATTERN = /^[A-Za-z0-9_+-]+(\/[A-Za-z0-9_+-]+)*$/
const LOCALE_PATTERN = /^[a-z]{2}(-[A-Za-z]{2,4})?$/

/** Profile fields are plain strings: empty means "leave unchanged" (PATCH semantics). */
export const profileSchema = z.object({
  firstName: z.string().trim().max(80, 'Must be 80 characters or fewer'),
  lastName: z.string().trim().max(80, 'Must be 80 characters or fewer'),
  timezone: z
    .string()
    .refine(
      (value) => value === '' || TZ_PATTERN.test(value),
      'Use a timezone such as Europe/Lisbon',
    ),
  locale: z
    .string()
    .refine((value) => value === '' || LOCALE_PATTERN.test(value), 'Use a locale such as en-GB'),
})

export const preferencesSchema = z.object({
  theme: z.enum(['SYSTEM', 'LIGHT', 'DARK']),
  dateFormat: z.string().trim().min(1, 'Date format is required').max(20, 'Too long'),
  firstDayOfWeek: z
    .number({ invalid_type_error: 'Enter a number' })
    .int('Whole numbers only')
    .min(1, 'Between 1 and 7')
    .max(7, 'Between 1 and 7'),
  decimalStyle: z.enum(['DOT', 'COMMA']),
  groupSeparator: z.string().min(1, 'Required').max(4, 'Too long'),
  currencySymbolPosition: z.enum(['BEFORE', 'AFTER']),
  numberFormat: z.string().trim().min(1, 'Required').max(24, 'Too long'),
  budgetAlertThresholdPct: z
    .number({ invalid_type_error: 'Enter a number' })
    .int('Whole numbers only')
    .min(1, 'Between 1 and 100')
    .max(100, 'Between 1 and 100'),
  goalReminderDays: z
    .number({ invalid_type_error: 'Enter a number' })
    .int('Whole numbers only')
    .min(0, 'Between 0 and 365')
    .max(365, 'Between 0 and 365'),
  recurringReminderDays: z
    .number({ invalid_type_error: 'Enter a number' })
    .int('Whole numbers only')
    .min(0, 'Between 0 and 30')
    .max(30, 'Between 0 and 30'),
  emailDigestEnabled: z.boolean(),
  reducedMotion: z.boolean(),
})

export const baseCurrencySchema = z.object({
  currencyCode: z.string().regex(/^[A-Za-z]{3}$/, 'Select an ISO-4217 currency code'),
})

/** POST /auth/change-password (ARCHITECTURE.md D.2): matches the backend's 8-72 rule. */
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z
      .string()
      .min(8, 'Use at least 8 characters')
      .max(72, 'Must be 72 characters or fewer'),
    confirmPassword: z.string().min(1, 'Confirm the new password'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  })

/** DELETE /users/me requires the exact typed phrase the backend expects. */
export const deleteAccountSchema = z.object({
  password: z.string().min(1, 'Enter your password'),
  confirmationText: z
    .string()
    .refine((value) => value === 'DELETE', 'Type DELETE to confirm'),
})

export type ProfileFormValues = z.infer<typeof profileSchema>
export type PreferencesFormValues = z.infer<typeof preferencesSchema>
export type BaseCurrencyFormValues = z.infer<typeof baseCurrencySchema>
export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>
export type DeleteAccountFormValues = { password: string; confirmationText: string }

/** Blank profile form — the resolver expects strings, so optional never shows `undefined`. */
export const EMPTY_PROFILE_FORM: ProfileFormValues = {
  firstName: '',
  lastName: '',
  timezone: '',
  locale: '',
}

/** Offered date formats, all of them valid `UserPreference.dateFormat` values. */
export const DATE_FORMAT_OPTIONS = [
  'yyyy-MM-dd',
  'dd/MM/yyyy',
  'MM/dd/yyyy',
  'dd.MM.yyyy',
  'd MMM yyyy',
] as const

/** ISO-8601 day numbers: 1 = Monday … 7 = Sunday (backend default 1). */
export const FIRST_DAY_OPTIONS = [
  { value: 1, label: 'Monday' },
  { value: 2, label: 'Tuesday' },
  { value: 3, label: 'Wednesday' },
  { value: 4, label: 'Thursday' },
  { value: 5, label: 'Friday' },
  { value: 6, label: 'Saturday' },
  { value: 7, label: 'Sunday' },
] as const
