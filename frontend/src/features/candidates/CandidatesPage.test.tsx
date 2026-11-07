import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { JAVA, pageOf, problem, renderAt, signIn, summary, userSummary } from '../../test/fixtures'
import { server } from '../../test/server'
import { CandidatesPage } from './CandidatesPage'

/** The total is split across elements (bold number), so match the paragraph's full text. */
const countText = (_: string, el: Element | null) => el?.tagName === 'P' && el.textContent === '12 candidates'

function setup() {
  signIn(userSummary(['RECRUITER']))
  const requests: URLSearchParams[] = []
  server.use(
    http.get('*/api/v1/skills', () => HttpResponse.json([JAVA])),
    http.get('*/api/v1/candidates', ({ request }) => {
      const params = new URL(request.url).searchParams
      requests.push(params)
      return HttpResponse.json(pageOf([summary(), summary({ id: 'cand-2', fullName: 'Ana Lopez', email: 'ana@example.com', status: 'DO_NOT_CONTACT', skills: [] })], { totalElements: 12, totalPages: 2 }))
    }),
  )
  renderAt(<CandidatesPage />)
  return requests
}

describe('CandidatesPage', () => {
  it('lists candidates with links, skills and status badges', async () => {
    setup()

    const link = await screen.findByRole('link', { name: 'Sam Patel' })
    expect(link).toHaveAttribute('href', '/candidates/cand-1')
    expect(within(screen.getByRole('table')).getByText('Java')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('PostgreSQL')).toBeInTheDocument()
    expect(within(screen.getByRole('table')).getByText('Do not contact')).toBeInTheDocument()
    expect(screen.getByText(countText)).toBeInTheDocument()
  })

  it('sends keyword, location, experience, source and skill filters only when searching', async () => {
    const requests = setup()
    await screen.findByRole('link', { name: 'Sam Patel' })
    expect(requests).toHaveLength(1) // initial load, no filters
    expect(requests[0]!.get('q')).toBeNull()

    await userEvent.type(screen.getByLabelText('Keyword'), 'java dev')
    await userEvent.type(screen.getByLabelText('Location'), 'london')
    await userEvent.type(screen.getByLabelText('Minimum years'), '5')
    await userEvent.selectOptions(screen.getByLabelText('Source'), 'REFERRAL')
    await userEvent.click(screen.getByRole('combobox', { name: 'Must have skills' }))
    await userEvent.click(await screen.findByRole('button', { name: /Java/ }))
    expect(requests).toHaveLength(1) // typing alone does not search

    await userEvent.click(screen.getByRole('button', { name: 'Search' }))

    await screen.findByText(countText)
    const last = requests.at(-1)!
    expect(last.get('q')).toBe('java dev')
    expect(last.get('location')).toBe('london')
    expect(last.get('minYears')).toBe('5')
    expect(last.get('source')).toBe('REFERRAL')
    expect(last.getAll('skillId')).toEqual([JAVA.id])
    expect(last.get('sort')).toBe('lastName,asc')
  })

  it('lets a skill filter be removed again', async () => {
    setup()
    await screen.findByRole('link', { name: 'Sam Patel' })
    await userEvent.click(screen.getByRole('combobox', { name: 'Must have skills' }))
    await userEvent.click(await screen.findByRole('button', { name: /Java/ }))

    const selected = screen.getByRole('list', { name: 'Selected skills' })
    await userEvent.click(within(selected).getByRole('button', { name: 'Remove Java filter' }))

    expect(screen.queryByRole('list', { name: 'Selected skills' })).not.toBeInTheDocument()
  })

  it('moves to the next page', async () => {
    const requests = setup()
    await screen.findByRole('link', { name: 'Sam Patel' })

    await userEvent.click(screen.getByRole('button', { name: 'Next' }))

    await screen.findByText(countText)
    expect(requests.at(-1)!.get('page')).toBe('1')
  })

  it('shows an empty state and an error state', async () => {
    signIn(userSummary(['RECRUITER']))
    server.use(http.get('*/api/v1/candidates', () => HttpResponse.json(pageOf([]))))
    const view = renderAt(<CandidatesPage />)
    expect(await screen.findByText('No candidates match your search.')).toBeInTheDocument()
    view.unmount()

    server.use(http.get('*/api/v1/candidates', () => problem(500, 'INTERNAL_ERROR', 'An unexpected error occurred.')))
    renderAt(<CandidatesPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred.')
  })
})
