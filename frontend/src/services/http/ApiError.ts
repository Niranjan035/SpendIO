import { isProblemDetails, type ProblemDetails } from '@/types/ProblemDetails'

/** Every non-2xx response is normalised into this shape. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; message: string }[]
  readonly traceId: string | null
  readonly retryAfter: number | null
  readonly payload: ProblemDetails | null

  constructor(status: number, payload: ProblemDetails | null, fallbackMessage?: string) {
    super(payload?.detail ?? payload?.title ?? fallbackMessage ?? `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.code = payload?.code ?? `HTTP_${status}`
    this.fieldErrors = Array.isArray(payload?.fieldErrors) ? payload.fieldErrors! : []
    this.traceId = typeof payload?.traceId === 'string' ? payload.traceId : null
    this.retryAfter =
      typeof payload?.retryAfter === 'number'
        ? payload.retryAfter
        : typeof payload?.retryAfterSeconds === 'number'
          ? (payload.retryAfterSeconds as number)
          : null
    this.payload = payload
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isNotFound(): boolean {
    return this.status === 404
  }

  get isValidation(): boolean {
    return this.status === 400 || this.status === 422
  }

  get isConflict(): boolean {
    return this.status === 409
  }

  get isRateLimited(): boolean {
    return this.status === 429
  }

  get isServer(): boolean {
    return this.status >= 500
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((f) => f.field === field)?.message
  }

  static from(status: number, body: unknown): ApiError {
    const payload = isProblemDetails(body) ? body : null
    return new ApiError(status, payload)
  }
}

/** Best-effort parsing of an error body, which may not be JSON at all. */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: unknown = null
  try {
    const text = await response.text()
    if (text) {
      try {
        body = JSON.parse(text)
      } catch {
        body = null
      }
    }
  } catch {
    body = null
  }
  return ApiError.from(response.status, body)
}
