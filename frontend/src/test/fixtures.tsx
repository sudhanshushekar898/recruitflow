import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { HttpResponse } from 'msw'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { createQueryClient } from '../app/queryClient'
import { authStore } from '../lib/authStore'
import type { CandidateSummary, CandidateView, LoginResponse, PageResponse, Role, UserSummary } from '../types'

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

export const JAVA = { id: 'skill-java', name: 'java', displayName: 'Java', category: 'Language' }
export const AWS = { id: 'skill-aws', name: 'aws', displayName: 'AWS', category: 'Cloud' }

export function candidateView(overrides: Partial<CandidateView> = {}): CandidateView {
  return {
    id: 'cand-1',
    firstName: 'Sam',
    lastName: 'Patel',
    email: 'sam@example.com',
    source: 'REFERRAL',
    status: 'ACTIVE',
    ownerId: 'owner-1',
    ownerName: 'Ana Lopez',
    retentionUntil: '2028-11-01T00:00:00Z',
    createdAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    version: 0,
    currentTitle: 'Java Developer',
    location: 'London',
    yearsExperience: 6,
    skills: [{ skillId: JAVA.id, name: 'java', displayName: 'Java', years: 6, level: 'ADVANCED' }],
    ...overrides,
  }
}

export function pageOf<T>(content: T[], extra: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra }
}

export function summary(overrides: Partial<CandidateSummary> = {}): CandidateSummary {
  return {
    id: 'cand-1',
    fullName: 'Sam Patel',
    email: 'sam@example.com',
    status: 'ACTIVE',
    skills: ['Java', 'PostgreSQL'],
    createdAt: '2026-11-01T10:00:00Z',
    currentTitle: 'Java Developer',
    location: 'London',
    yearsExperience: 6,
    ownerName: 'Ana Lopez',
    ...overrides,
  }
}

export function renderAt(ui: ReactElement, path = '/') {
  return render(
    <QueryClientProvider client={createQueryClient({ retry: false })}>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}
