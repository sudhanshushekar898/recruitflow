import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { authStore } from '../../lib/authStore'
import { loginResponse, problem, renderAt, userSummary } from '../../test/fixtures'
import { server } from '../../test/server'
import { LoginPage } from './LoginPage'

function renderLogin() {
  return renderAt(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<p>Home page</p>} />
    </Routes>,
    '/login',
  )
}

describe('LoginPage', () => {
  it('validates required fields before calling the API', async () => {
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your email')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
  })

  it('signs in and goes to the home page', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json(loginResponse(userSummary(['RECRUITER'])))))
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-Battery-9')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Home page')).toBeInTheDocument()
    expect(authStore.getSnapshot().status).toBe('authenticated')
  })

  it('shows a generic message for wrong credentials and stays on the page', async () => {
    server.use(http.post('*/api/v1/auth/login', () => problem(401, 'UNAUTHENTICATED', 'Invalid email or password.')))
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(authStore.getSnapshot().status).not.toBe('authenticated')
  })

  it('explains a locked account', async () => {
    server.use(http.post('*/api/v1/auth/login', () => problem(423, 'ACCOUNT_LOCKED', 'Too many failed attempts. Try again later.')))
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'ana@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'whatever')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/wait a few minutes/i))
  })
})
