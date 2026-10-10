/** Settings DTOs mirroring the backend contracts (ARCHITECTURE.md D.3, D.4). */

export type Theme = 'SYSTEM' | 'LIGHT' | 'DARK'

export type DecimalStyle = 'DOT' | 'COMMA'

export type SymbolPosition = 'BEFORE' | 'AFTER'

/** GET/PATCH /users/me */
export interface UserProfile {
  id: number
  email: string
  firstName: string | null
  lastName: string | null
  displayName: string | null
  timezone: string | null
  locale: string | null
  baseCurrencyCode: string
  emailVerified: boolean
  createdAt: string
}

/** GET/PUT /users/me/preferences */
export interface UserPreferences {
  theme: Theme
  dateFormat: string
  firstDayOfWeek: number
  decimalStyle: DecimalStyle
  groupSeparator: string
  currencySymbolPosition: SymbolPosition
  numberFormat: string
  budgetAlertThresholdPct: number
  goalReminderDays: number
  recurringReminderDays: number
  emailDigestEnabled: boolean
  reducedMotion: boolean
}

/** GET /currencies */
export interface Currency {
  code: string
  name: string
  minorUnit: number
  symbol: string | null
  isActive: boolean
}

/** PATCH /users/me — omitted fields are left untouched. */
export interface UpdateProfileInput {
  firstName?: string | null
  lastName?: string | null
  timezone?: string | null
  locale?: string | null
}

/** PUT /users/me/base-currency */
export interface ChangeBaseCurrencyInput {
  currencyCode: string
  revalueExistingTransactions?: boolean
}

/** POST /auth/change-password */
export interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}

/** GET /auth/sessions (ARCHITECTURE.md D.2, E.2). */
export interface UserSession {
  id: string
  deviceLabel: string | null
  ipAddress: string | null
  current: boolean
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string
}

/** POST /users/me/export (ARCHITECTURE.md E.3, Phase 14). */
export interface UserDataExport {
  exportedAt: string
  data: Record<string, unknown>
}

/** DELETE /users/me */
export interface DeleteAccountInput {
  password: string
  confirmationText: string
}

export type AuditActorType = 'USER' | 'SYSTEM' | 'JOB'

/** GET /audit-logs (ARCHITECTURE.md E.3, Phase 14). */
export interface AuditLogEntry {
  id: number
  actorType: AuditActorType
  action: string
  entityType: string | null
  entityId: number | null
  ipAddress: string | null
  traceId: string | null
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  createdAt: string
}

export interface AuditLogPage {
  content: AuditLogEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
