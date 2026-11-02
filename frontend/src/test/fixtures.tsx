import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { HttpResponse } from 'msw'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { createQueryClient } from '../app/queryClient'
import { authStore } from '../lib/authStore'
import type { LoginResponse, Role, UserSummary } from '../types'

export function userSummary(roles: Role[], overrides: Partial<UserSummary> = {}): UserSummary {
  return {
    id: '0190aaaa-0000-7000-8000-000000000001',
    email: 'ana@example.com',
    name: 'Ana Lopez',
    roles,
    mustChangePassword: false,
    ...overrides,
  }
}

export function loginResponse(user: UserSummary, accessToken = 'access-1'): LoginResponse {
  return { accessToken, tokenType: 'Bearer', expiresInSeconds: 900, user }
}

export function signIn(user: UserSummary, accessToken = 'access-1') {
  authStore.setSession(accessToken, user)
}

export function problem(status: number, code: string, detail: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json({ status, code, detail, ...extra }, { status, headers: { 'Content-Type': 'application/problem+json' } })
}

export function renderAt(ui: ReactElement, path = '/') {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}
