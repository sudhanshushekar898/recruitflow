export interface FieldError {
  field: string
  message: string
}

/** An RFC 7807 problem+json response from the API, or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly correlationId?: string
  /** The full problem body, for extension members such as existingCandidateId. */
  readonly extensions: Record<string, unknown>

  constructor(init: {
    status: number
    code: string
    detail: string
    fieldErrors?: FieldError[]
    correlationId?: string
    extensions?: Record<string, unknown>
  }) {
    super(init.detail)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.fieldErrors = init.fieldErrors ?? []
    this.correlationId = init.correlationId
    this.extensions = init.extensions ?? {}
  }
}

export async function toApiError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {}
  try {
    body = (await response.json()) as Record<string, unknown>
  } catch {
    // non-JSON error (for example a proxy page): fall through to generic values
  }
  const errors = Array.isArray(body.errors) ? (body.errors as FieldError[]) : []
  return new ApiError({
    status: response.status,
    code: typeof body.code === 'string' ? body.code : 'UNKNOWN_ERROR',
    detail: typeof body.detail === 'string' ? body.detail : 'Something went wrong. Please try again.',
    fieldErrors: errors,
    correlationId: typeof body.correlationId === 'string' ? body.correlationId : undefined,
    extensions: body,
  })
}

export function networkError(): ApiError {
  return new ApiError({ status: 0, code: 'NETWORK_ERROR', detail: 'Cannot reach the server. Check your connection and retry.' })
}
