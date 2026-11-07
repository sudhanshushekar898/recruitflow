import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Spinner } from '../../components/ui'
import type { Role } from '../../types'
import { useAuth } from './useAuth'

/** Signed-in users only. A user who must change their password is held on that page until they do. */
export function RequireAuth() {
  const { status, user, signedOut } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <Spinner label="Restoring session" />
  if (!user) {
    // Visitors and people who just signed out see the product homepage. Only an expired session (or a deep
    // link) returns to the page after sign-in, so the next person never lands on someone else's last page.
    if (signedOut || location.pathname === '/') return <Navigate to="/welcome" replace />
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  return <Outlet />
}

/** Hides pages the role cannot use. The API enforces the same rules; this only improves the experience. */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { hasAnyRole } = useAuth()
  if (!hasAnyRole(...roles)) return <Forbidden />
  return <>{children}</>
}

export function Forbidden() {
  return (
    <div role="alert" className="py-16 text-center">
      <h1 className="text-2xl font-semibold">Access denied</h1>
      <p className="mt-2 text-slate-600">Your role does not allow you to view this page.</p>
    </div>
  )
}

/** Public pages (login, reset, ...) send signed-in users to the app instead. */
export function PublicOnly() {
  const { status, user } = useAuth()
  if (status === 'loading') return <Spinner label="Restoring session" />
  if (user) return <Navigate to="/" replace />
  return <Outlet />
}
