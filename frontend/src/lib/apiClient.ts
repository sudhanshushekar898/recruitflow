import type { LoginResponse } from '../types'
import { authStore } from './authStore'
import { ApiError, networkError, toApiError } from './errors'

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | boolean | undefined | null>
  /** false for endpoints that must not carry a token or trigger a refresh (login, reset, ...). */
  auth?: boolean
  /** Extra headers, for example X-Requested-With on cookie-authenticated endpoints. */
  headers?: Record<string, string>
  signal?: AbortSignal
}

// Absolute URLs keep fetch working outside a browser page (tests) and are same-origin in the browser.
function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = new URL(path, window.location.origin)
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  })
  return url.toString()
}

let refreshInFlight: Promise<boolean> | null = null

/**
 * Exchanges the HttpOnly refresh cookie for a new access token. Concurrent callers share one request:
 * the server rotates the cookie on every use, so two parallel refreshes would look like token theft.
 */
export function refreshSession(): Promise<boolean> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function doRefresh(): Promise<boolean> {
  try {
    const response = await fetch(buildUrl('/api/v1/auth/refresh'), {
      method: 'POST',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
      credentials: 'same-origin',
    })
    if (!response.ok) {
      authStore.clear()
      return false
    }
    const data = (await response.json()) as LoginResponse
    authStore.setSession(data.accessToken, data.user)
    return true
  } catch {
    // Server unreachable: keep the current state; the caller sees the failed request.
    return false
  }
}

async function send(path: string, options: RequestOptions, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  try {
    return await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'same-origin',
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw networkError()
  }
}

export async function api<T = void>(path: string, options: RequestOptions = {}): Promise<T> {
  const useAuth = options.auth !== false
  let response = await send(path, options, useAuth ? authStore.getSnapshot().accessToken : null)

  if (response.status === 401 && useAuth) {
    // Access tokens live 15 minutes: try one refresh, then replay the request once.
    if (await refreshSession()) {
      response = await send(path, options, authStore.getSnapshot().accessToken)
    }
  }

  if (!response.ok) throw await toApiError(response)
  // 202/204 and other bodiless successes (forgot-password, logout) have nothing to parse.
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code)
}
