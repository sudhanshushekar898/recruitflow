import { Outlet, Route, Routes } from 'react-router-dom'
import { CandidateDetailPage } from '../features/candidates/CandidateDetailPage'
import { CandidateFormPage } from '../features/candidates/CandidateFormPage'
import { CandidatesPage } from '../features/candidates/CandidatesPage'
import { AcceptInvitePage, ChangePasswordPage, ForgotPasswordPage, ResetPasswordPage } from '../features/auth/PasswordPages'
import { LoginPage } from '../features/auth/LoginPage'
import { PublicOnly, RequireAuth, RequireRole } from '../features/auth/guards'
import { UsersPage } from '../features/users/UsersPage'
import { AppLayout } from './AppLayout'
import { DashboardPage, NotFoundPage } from './DashboardPage'

/** Candidate pages are for recruiters and admins; interviewers get scoped access in a later milestone. */
function RecruiterArea() {
  return (
    <RequireRole roles={['ADMIN', 'RECRUITER']}>
      <Outlet />
    </RequireRole>
  )
}

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
          <Route element={<RecruiterArea />}>
            <Route path="/candidates" element={<CandidatesPage />} />
            <Route path="/candidates/new" element={<CandidateFormPage />} />
            <Route path="/candidates/:id" element={<CandidateDetailPage />} />
            <Route path="/candidates/:id/edit" element={<CandidateFormPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
