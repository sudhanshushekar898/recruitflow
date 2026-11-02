import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { problem, renderAt } from '../../test/fixtures'
import { server } from '../../test/server'
import { AcceptInvitePage, ForgotPasswordPage, ResetPasswordPage } from './PasswordPages'

describe('password pages', () => {
  it('rejects a short password locally and mismatched confirmation', async () => {
    renderAt(<ResetPasswordPage />, '/reset-password?token=abc')

    await userEvent.type(screen.getByLabelText('New password'), 'short')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'different')
    await userEvent.click(screen.getByRole('button', { name: 'Reset password' }))

    expect(await screen.findByText('Use at least 12 characters')).toBeInTheDocument()
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
  })

  it('sends the token and password, then goes to login', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/accept-invite', async ({ request }) => {
        body = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderAt(
      <Routes>
        <Route path="/accept-invite" element={<AcceptInvitePage />} />
        <Route path="/login" element={<p>Login page</p>} />
      </Routes>,
      '/accept-invite?token=tok-123',
    )

    await userEvent.type(screen.getByLabelText('New password'), 'A-Fresh-Passphrase-2026')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'A-Fresh-Passphrase-2026')
    await userEvent.click(screen.getByRole('button', { name: 'Set password' }))

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(body).toEqual({ token: 'tok-123', password: 'A-Fresh-Passphrase-2026' })
  })

  it('shows the server message for an expired link', async () => {
    server.use(http.post('*/api/v1/auth/reset-password', () => problem(400, 'INVALID_TOKEN', 'This link is invalid or has expired.')))
    renderAt(<ResetPasswordPage />, '/reset-password?token=old')

    await userEvent.type(screen.getByLabelText('New password'), 'A-Fresh-Passphrase-2026')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'A-Fresh-Passphrase-2026')
    await userEvent.click(screen.getByRole('button', { name: 'Reset password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('invalid or has expired')
  })

  it('explains an incomplete link when the token is missing', () => {
    renderAt(<AcceptInvitePage />, '/accept-invite')

    expect(screen.getByRole('alert')).toHaveTextContent(/incomplete/i)
  })

  it('gives the same confirmation for any email on the forgot-password page', async () => {
    server.use(http.post('*/api/v1/auth/forgot-password', () => new HttpResponse(null, { status: 202 })))
    renderAt(<ForgotPasswordPage />, '/forgot-password')

    await userEvent.type(screen.getByLabelText('Email'), 'ghost@example.com')
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByRole('status')).toHaveTextContent('If an account exists')
  })
})
