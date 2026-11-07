import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authStore } from '../lib/authStore'
import { loginResponse, renderAt, signIn, userSummary } from '../test/fixtures'
import { server } from '../test/server'
import { AppRoutes } from './AppRoutes'
import { visibleNav } from './navigation'

const emptyUsers = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }

describe('route guards', () => {
  it('sends anonymous visitors to the login page', async () => {
    authStore.clear()
    renderAt(<AppRoutes />, '/users')

    expect(await screen.findByRole('heading', { name: 'Sign in to RecruitFlow' })).toBeInTheDocument()
  })

  it('shows a spinner while the session is being restored', () => {
    renderAt(<AppRoutes />, '/')

    expect(screen.getByRole('status', { name: 'Restoring session' })).toBeInTheDocument()
  })

  it('holds a user who must change their password on that page', async () => {
    signIn(userSummary(['ADMIN'], { mustChangePassword: true }))
    renderAt(<AppRoutes />, '/users')

    expect(await screen.findByRole('heading', { name: 'Change your password' })).toBeInTheDocument()
    expect(screen.getByText(/must choose a new password/i)).toBeInTheDocument()
  })

  it('redirects signed-in users away from the login page', async () => {
    signIn(userSummary(['RECRUITER']))
    renderAt(<AppRoutes />, '/login')

    expect(await screen.findByRole('heading', { name: /Welcome, Ana/ })).toBeInTheDocument()
  })

  it('denies the users page to non-admins', async () => {
    signIn(userSummary(['RECRUITER']))
    renderAt(<AppRoutes />, '/users')

    expect(await screen.findByRole('alert')).toHaveTextContent('Access denied')
  })

  it('denies the candidate pages to interviewers', async () => {
    signIn(userSummary(['INTERVIEWER']))
    renderAt(<AppRoutes />, '/candidates/abc')

    expect(await screen.findByRole('alert')).toHaveTextContent('Access denied')
  })

  it('shows the users page to admins', async () => {
    signIn(userSummary(['ADMIN']))
    server.use(
      http.get('*/api/v1/users', () =>
        HttpResponse.json({
          ...emptyUsers,
          content: [
            { id: '1', email: 'pat@example.com', firstName: 'Pat', lastName: 'Lee', roles: ['RECRUITER'], enabled: true, pendingInvite: true, createdAt: '2026-11-02T10:00:00Z', version: 0 },
          ],
          totalElements: 1,
          totalPages: 1,
        }),
      ),
    )
    renderAt(<AppRoutes />, '/users')

    expect(await screen.findByText('pat@example.com')).toBeInTheDocument()
    expect(screen.getByText('Invite pending')).toBeInTheDocument()
  })

  it('shows not-found inside the layout for unknown pages', async () => {
    signIn(userSummary(['RECRUITER']))
    renderAt(<AppRoutes />, '/nope')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})

describe('navigation', () => {
  it('lists admin-only entries only for admins', () => {
    expect(visibleNav(['RECRUITER']).map((i) => i.label)).toEqual(['Dashboard', 'Jobs', 'Candidates', 'Interviews', 'My interviews'])
    expect(visibleNav(['ADMIN']).map((i) => i.label)).toEqual(['Dashboard', 'Jobs', 'Candidates', 'Interviews', 'My interviews', 'Users'])
    expect(visibleNav(['INTERVIEWER']).map((i) => i.label)).toEqual(['Dashboard', 'My interviews'])
  })

  it('renders the sidebar for the signed-in role and signs out', async () => {
    signIn(userSummary(['RECRUITER']))
    server.use(http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })))
    renderAt(<AppRoutes />, '/')

    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(within(nav).getByText('Dashboard')).toBeInTheDocument()
    expect(within(nav).queryByText('Users')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    // signing out goes to the homepage, never back to this user's last page
    expect(await screen.findByRole('heading', { name: /Hire faster, with every candidate/ })).toBeInTheDocument()
    expect(authStore.getSnapshot().status).toBe('anonymous')
  })

  it('shows the homepage to visitors and sends them to sign in from there', async () => {
    authStore.clear()
    renderAt(<AppRoutes />, '/')

    expect(await screen.findByRole('heading', { name: /Hire faster, with every candidate/ })).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('link', { name: 'Sign in' })[0]!)

    expect(await screen.findByRole('heading', { name: 'Sign in to RecruitFlow' })).toBeInTheDocument()
  })

  it('returns a visitor to the page they asked for after signing in, but not after a sign-out', async () => {
    authStore.clear()
    server.use(
      http.post('*/api/v1/auth/login', () => HttpResponse.json(loginResponse(userSummary(['ADMIN'])))),
      http.get('*/api/v1/users', () => HttpResponse.json(emptyUsers)),
    )
    renderAt(<AppRoutes />, '/users')

    await userEvent.type(await screen.findByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-Battery-9')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument()
  })

  it('keeps the session restore result available to the router', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(loginResponse(userSummary(['ADMIN'])))))
    const { bootstrapSession } = await import('../features/auth/useAuth')
    renderAt(<AppRoutes />, '/')

    await bootstrapSession()

    expect(await screen.findByRole('heading', { name: /Welcome, Ana/ })).toBeInTheDocument()
  })
})
