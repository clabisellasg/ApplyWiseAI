const DEFAULT_API_BASE_URL = 'http://localhost:8080'

export const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL
).replace(/\/$/, '')

type ApiErrorBody = {
  error?: unknown
  message?: unknown
  detail?: unknown
  errors?: unknown
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function errorText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function validationMessages(errors: unknown): string[] {
  if (!Array.isArray(errors)) {
    return []
  }

  return errors.flatMap((error) => {
    if (!isRecord(error)) {
      return []
    }

    const field = errorText(error.field)
    const message = errorText(error.defaultMessage) ?? errorText(error.message)

    if (!message) {
      return []
    }

    return [field ? `${field}: ${message}` : message]
  })
}

function apiErrorMessage(body: unknown, fallback: string): string {
  if (!isRecord(body)) {
    return fallback
  }

  const errorBody = body as ApiErrorBody
  const messages = validationMessages(errorBody.errors)

  if (messages.length > 0) {
    return messages.join(' ')
  }

  return (
    errorText(errorBody.message) ??
    errorText(errorBody.detail) ??
    errorText(errorBody.error) ??
    fallback
  )
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)

  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }

    throw new ApiError('Unable to connect to the backend. Check that it is running and try again.', 0)
  }

  if (!response.ok) {
    let body: unknown = null

    try {
      body = await response.json()
    } catch {
      // The status fallback below handles empty and non-JSON error responses.
    }

    const fallback = response.statusText || `Request failed with status ${response.status}`
    throw new ApiError(apiErrorMessage(body, fallback), response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}
