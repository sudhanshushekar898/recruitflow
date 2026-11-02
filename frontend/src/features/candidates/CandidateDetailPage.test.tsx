import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { candidateView, problem, renderAt, signIn, userSummary } from '../../test/fixtures'
import { server } from '../../test/server'
import { CandidateDetailPage } from './CandidateDetailPage'

function renderDetail(candidate = candidateView()) {
  signIn(userSummary(['RECRUITER']))
  server.use(
    http.get('*/api/v1/candidates/cand-1', () => HttpResponse.json(candidate)),
    http.get('*/api/v1/candidates/cand-1/notes', () =>
      HttpResponse.json([{ id: 'n1', authorId: 'a', authorName: 'Ana Lopez', body: 'Called, keen on remote roles', createdAt: '2026-11-01T10:00:00Z' }]),
    ),
  )
  return renderAt(
    <Routes>
      <Route path="/candidates/:id" element={<CandidateDetailPage />} />
    </Routes>,
    '/candidates/cand-1',
  )
}

describe('CandidateDetailPage', () => {
  it('shows the profile, skills with years and level, owner and retention date', async () => {
    renderDetail(candidateView({ summary: 'Strong backend engineer', linkedinUrl: 'https://linkedin.com/in/sam' }))

    expect(await screen.findByRole('heading', { name: 'Sam Patel' })).toBeInTheDocument()
    expect(screen.getByText('Java Developer')).toBeInTheDocument()
    expect(screen.getByText('6 years')).toBeInTheDocument()
    expect(screen.getByText(/Java/, { selector: 'li' })).toHaveTextContent('6y, Advanced')
    expect(screen.getByText('Strong backend engineer')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'https://linkedin.com/in/sam' })).toHaveAttribute('rel', 'noreferrer noopener')
    expect(screen.getByText('1 Nov 2028')).toBeInTheDocument()
    expect(await screen.findByText('Called, keen on remote roles')).toBeInTheDocument()
    expect(screen.getByText(/No lawful basis recorded yet/)).toBeInTheDocument()
  })

  it('adds a note and clears the box', async () => {
    let body: unknown
    renderDetail()
    server.use(
      http.post('*/api/v1/candidates/cand-1/notes', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ id: 'n2', authorId: 'a', authorName: 'Ana Lopez', body: 'Follow up Friday', createdAt: '2026-11-02T10:00:00Z' }, { status: 201 })
      }),
    )
    await screen.findByRole('heading', { name: 'Sam Patel' })
    const add = screen.getByRole('button', { name: 'Add note' })
    expect(add).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Add a note'), '  Follow up Friday ')
    await userEvent.click(add)

    expect(await screen.findByDisplayValue('')).toBeInTheDocument()
    expect(body).toEqual({ body: 'Follow up Friday' })
  })

  it('archives only after a confirmation step', async () => {
    let archived = false
    renderDetail()
    server.use(
      http.post('*/api/v1/candidates/cand-1/archive', () => {
        archived = true
        return HttpResponse.json(candidateView({ status: 'ARCHIVED' }))
      }),
    )
    await screen.findByRole('heading', { name: 'Sam Patel' })

    await userEvent.click(screen.getByRole('button', { name: 'Archive' }))
    expect(archived).toBe(false)
    await userEvent.click(screen.getByRole('button', { name: 'Keep' }))
    expect(screen.getByRole('button', { name: 'Archive' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Archive' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirm archive' }))

    await screen.findByRole('button', { name: 'Archive' }) // list refetch returns the mocked ACTIVE record again
    expect(archived).toBe(true)
  })

  it('offers Restore and no Edit for an archived candidate', async () => {
    renderDetail(candidateView({ status: 'ARCHIVED' }))

    expect(await screen.findByRole('button', { name: 'Restore' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.getByText('Archived')).toBeInTheDocument()
  })

  it('records consent', async () => {
    let body: unknown
    renderDetail()
    server.use(
      http.put('*/api/v1/candidates/cand-1/consent', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(candidateView({ consent: { legalBasis: 'CONSENT', givenAt: '2026-10-01T00:00:00Z' } }))
      }),
    )
    await screen.findByRole('heading', { name: 'Sam Patel' })

    await userEvent.click(screen.getByRole('button', { name: 'Record consent' }))
    await userEvent.type(screen.getByLabelText('Date given'), '2026-10-01')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('button', { name: /consent/ })).toBeInTheDocument()
    expect(body).toEqual({ legalBasis: 'CONSENT', givenAt: '2026-10-01T00:00:00Z', withdrawn: false })
  })

  it('reports a candidate that does not exist', async () => {
    signIn(userSummary(['RECRUITER']))
    server.use(http.get('*/api/v1/candidates/cand-1', () => problem(404, 'RESOURCE_NOT_FOUND', 'Candidate not found.')))
    renderAt(
      <Routes>
        <Route path="/candidates/:id" element={<CandidateDetailPage />} />
      </Routes>,
      '/candidates/cand-1',
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('This candidate does not exist.')
  })
})
