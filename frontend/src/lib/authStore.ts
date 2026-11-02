import type { UserSummary } from '../types'

export type AuthState =
  | { status: 'loading'; accessToken: null; user: null }
  | { status: 'anonymous'; accessToken: null; user: null }
  | { status: 'authenticated'; accessToken: string; user: UserSummary }

const LOADING: AuthState = { status: 'loading', accessToken: null, user: null }
const ANONYMOUS: AuthState = { status: 'anonymous', accessToken: null, user: null }

let state: AuthState = LOADING
const listeners = new Set<() => void>()

function publish(next: AuthState) {
  state = next
  listeners.forEach((listener) => listener())
}

/**
 * The access token lives only in this module's memory: never in localStorage or cookies,
 * so an XSS payload cannot read it from storage. A page reload restores the session through the refresh cookie.
 */
export const authStore = {
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
  getSnapshot: () => state,
  setSession(accessToken: string, user: UserSummary) {
    publish({ status: 'authenticated', accessToken, user })
  },
  clear() {
    publish(ANONYMOUS)
  },
  reset() {
    publish(LOADING)
  },
}
