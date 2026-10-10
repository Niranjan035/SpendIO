/** RFC-9457 Problem Detail as emitted by the SpendIO backend. */
export interface FieldError {
  field: string
  message: string
}

export interface ProblemDetails {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  /** Stable machine code, e.g. `VALIDATION_ERROR`, `AUTH_TOKEN_EXPIRED`. */
  code?: string
  fieldErrors?: FieldError[]
  traceId?: string
  /** Seconds until the caller may retry (429 only). */
  retryAfter?: number
  [extra: string]: unknown
}

export function isProblemDetails(value: unknown): value is ProblemDetails {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ProblemDetails).status === 'number'
  )
}
