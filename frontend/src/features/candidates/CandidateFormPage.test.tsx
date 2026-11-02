import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { JAVA, candidateView, problem, renderAt, signIn, userSummary } from '../../test/fixtures'
import { server } from '../../test/server'
import { CandidateFormPage } from './CandidateFormPage'

function renderForm(path: string) {
  signIn(userSummary(['RECRUITER']))
  server.use(http.get('*/api/v1/skills', () => HttpResponse.json([JAVA])))
  return renderAt(
    <Routes>
      <Route path="/candidates/new" element={<CandidateFormPage />} />
      <Route path="/candidates/:id/edit" element={<CandidateFormPage />} />
      <Route path="/candidates/:id" element={<p>Detail page</p>} />
    </Routes>,
    path,
  )
}

async function fillRequired() {
  await userEvent.type(screen.getByLabelText('First name'), 'Sam')
  await userEvent.type(screen.getByLabelText('Last name'), 'Patel')
  await userEvent.type(screen.getByLabelText('Email'), 'sam@example.com')
}

describe('CandidateFormPage (create)', () => {
  it('validates required and malformed fields before calling the API', async () => {
    renderForm('/candidates/new')

    await userEvent.type(screen.getByLabelText('Email'), 'nope')
    await userEvent.type(screen.getByLabelText('Phone'), 'call me')
    await userEvent.type(screen.getByLabelText('LinkedIn URL'), 'http://insecure.example.com')
    await userEvent.type(screen.getByLabelText('Years of experience'), '99')
    await userEvent.click(screen.getByRole('button', { name: 'Create candidate' }))

    expect(await screen.findByText('Enter a first name')).toBeInTheDocument()
    expect(screen.getByText('Enter a last name')).toBeInTheDocument()
    expect(screen.getByText('Enter a valid email')).toBeInTheDocument()
    expect(screen.getByText('Use digits, spaces and + ( ) -')).toBeInTheDocument()
    expect(screen.getByText('Must start with https://')).toBeInTheDocument()
    expect(screen.getByText('Enter a number from 0 to 60')).toBeInTheDocument()
  })

  it('creates a candidate with skills and drops empty optional fields', async () => {
    let body: Record<string, unknown> = {}
    renderForm('/candidates/new')
    server.use(
      http.post('*/api/v1/candidates', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(candidateView({ id: 'new-1' }), { status: 201 })
      }),
    )

    await fillRequired()
    await userEvent.type(screen.getByLabelText('Years of experience'), '6')
    await userEvent.click(screen.getByRole('combobox', { name: 'Skills' }))
    await userEvent.click(await screen.findByRole('button', { name: /Java/ }))
    await userEvent.type(screen.getByLabelText('Years of Java'), '4')
    await userEvent.selectOptions(screen.getByLabelText('Level of Java'), 'ADVANCED')
    await userEvent.click(screen.getByRole('button', { name: 'Create candidate' }))

    expect(await screen.findByText('Detail page')).toBeInTheDocument()
    expect(body).toMatchObject({
      firstName: 'Sam', lastName: 'Patel', email: 'sam@example.com', source: 'OTHER', yearsExperience: 6,
      skills: [{ skillId: JAVA.id, years: 4, level: 'ADVANCED' }],
    })
    expect(body).not.toHaveProperty('phone')
    expect(body.consent).toBeUndefined()
  })

  it('sends consent only when a legal basis is chosen, and requires the date', async () => {
    let body: Record<string, unknown> = {}
    renderForm('/candidates/new')
    server.use(
      http.post('*/api/v1/candidates', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(candidateView({ id: 'new-1' }), { status: 201 })
      }),
    )
    await fillRequired()
    await userEvent.selectOptions(screen.getByLabelText('Legal basis'), 'CONSENT')

    await userEvent.click(screen.getByRole('button', { name: 'Create candidate' }))
    expect(await screen.findByText('Enter the date consent was given')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Date given'), '2026-10-01')
    await userEvent.click(screen.getByRole('button', { name: 'Create candidate' }))

    expect(await screen.findByText('Detail page')).toBeInTheDocument()
    expect(body.consent).toEqual({ legalBasis: 'CONSENT', givenAt: '2026-10-01T00:00:00Z', withdrawn: false })
  })

  it('points to the existing record when the email is a duplicate', async () => {
    renderForm('/candidates/new')
    server.use(
      http.post('*/api/v1/candidates', () =>
        problem(409, 'DUPLICATE_CANDIDATE', 'A candidate with this email already exists.', { existingCandidateId: 'existing-9' }),
      ),
    )

    await fillRequired()
    await userEvent.click(screen.getByRole('button', { name: 'Create candidate' }))

    const link = await screen.findByRole('link', { name: 'Open the existing record' })
    expect(link).toHaveAttribute('href', '/candidates/existing-9')
    expect(screen.getByText('A candidate with this email already exists')).toBeInTheDocument()
  })
})

describe('CandidateFormPage (edit)', () => {
  it('loads the candidate, sends the version with the changes and replaces skills', async () => {
    const calls: { patch?: Record<string, unknown>; put?: Record<string, unknown> } = {}
    renderForm('/candidates/cand-1/edit')
    server.use(
      http.get('*/api/v1/candidates/cand-1', () => HttpResponse.json(candidateView({ version: 3 }))),
      http.patch('*/api/v1/candidates/cand-1', async ({ request }) => {
        calls.patch = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(candidateView({ version: 4 }))
      }),
      http.put('*/api/v1/candidates/cand-1/skills', async ({ request }) => {
        calls.put = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(candidateView({ version: 4 }))
      }),
    )

    const title = await screen.findByLabelText('Current title')
    expect(screen.getByLabelText('First name')).toHaveValue('Sam')
    await userEvent.clear(title)
    await userEvent.type(title, 'Tech Lead')
    await userEvent.click(screen.getByRole('button', { name: 'Remove Java' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Detail page')).toBeInTheDocument()
    expect(calls.patch).toMatchObject({ currentTitle: 'Tech Lead', version: 3, email: 'sam@example.com' })
    expect(calls.put).toEqual({ skills: [] })
  })

  it('explains a version conflict', async () => {
    renderForm('/candidates/cand-1/edit')
    server.use(
      http.get('*/api/v1/candidates/cand-1', () => HttpResponse.json(candidateView())),
      http.patch('*/api/v1/candidates/cand-1', () => problem(409, 'STALE_VERSION', 'The resource was modified by someone else.')),
    )

    await screen.findByLabelText('First name')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Someone else changed this candidate')
  })

  it('does not allow editing an archived candidate', async () => {
    renderForm('/candidates/cand-1/edit')
    server.use(http.get('*/api/v1/candidates/cand-1', () => HttpResponse.json(candidateView({ status: 'ARCHIVED' }))))

    expect(await screen.findByText(/Archived candidates cannot be edited/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument()
  })
})
