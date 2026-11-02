import { Route, Routes } from 'react-router-dom'
import { AcceptInvitePage, ChangePasswordPage, ForgotPasswordPage, ResetPasswordPage } from '../features/auth/PasswordPages'
import { LoginPage } from '../features/auth/LoginPage'
import { PublicOnly, RequireAuth, RequireRole } from '../features/auth/guards'
import { UsersPage } from '../features/users/UsersPage'
import { AppLayout } from './AppLayout'
import { DashboardPage, NotFoundPage } from './DashboardPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/accept-invite" element={<AcceptInvitePage />} />
      </Route>

      <Route element={<RequireAuth />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route
            path="/users"
            element={
              <RequireRole roles={['ADMIN']}>
                <UsersPage />
              </RequireRole>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
