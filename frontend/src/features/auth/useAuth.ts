import { useQueryClient } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import { api, refreshSession } from '../../lib/apiClient'
import { authStore } from '../../lib/authStore'
import type { LoginResponse, Role } from '../../types'

export function useAuth() {
  const state = useSyncExternalStore(authStore.subscribe, authStore.getSnapshot)
  const queryClient = useQueryClient()

  return {
    status: state.status,
    user: state.user,
    hasAnyRole: (...roles: Role[]) => state.user?.roles.some((role) => roles.includes(role)) ?? false,
    async login(email: string, password: string) {
      const data = await api<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password }, auth: false })
      authStore.setSession(data.accessToken, data.user)
      return data.user
    },
    /** Replaces the session after a password change, which ends all other sessions server-side. */
    async changePassword(currentPassword: string, newPassword: string) {
      const data = await api<LoginResponse>('/api/v1/auth/change-password', {
        method: 'POST',
        body: { currentPassword, newPassword },
      })
      authStore.setSession(data.accessToken, data.user)
    },
    async logout() {
      try {
        await api('/api/v1/auth/logout', { method: 'POST', auth: false, headers: { 'X-Requested-With': 'XMLHttpRequest' } })
      } catch {
        // The local session is cleared regardless; a failed call only leaves a stale server-side token to expire.
      } finally {
        authStore.clear()
        queryClient.clear()
      }
    },
  }
}

/** Restores a session from the refresh cookie once at startup; anonymous if there is none. */
export async function bootstrapSession(): Promise<void> {
  if (!(await refreshSession())) authStore.clear()
}
