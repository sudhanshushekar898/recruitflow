import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { AWS, JAVA, renderAt, signIn, userSummary } from '../../test/fixtures'
import { server } from '../../test/server'
import { SkillPicker } from './SkillPicker'

describe('SkillPicker', () => {
  it('searches the catalog as you type and reports the chosen skill', async () => {
    signIn(userSummary(['RECRUITER']))
    const queries: string[] = []
    server.use(
      http.get('*/api/v1/skills', ({ request }) => {
        const q = new URL(request.url).searchParams.get('q') ?? ''
        queries.push(q)
        return HttpResponse.json(q === 'k8s' ? [{ id: 'k', name: 'kubernetes', displayName: 'Kubernetes' }] : [JAVA, AWS])
      }),
    )
    const onPick = vi.fn()
    renderAt(<SkillPicker onPick={onPick} />)

    await userEvent.type(screen.getByRole('combobox', { name: 'Add a skill' }), 'k8s')
    await userEvent.click(await screen.findByRole('button', { name: /Kubernetes/ }))

    expect(onPick).toHaveBeenCalledWith(expect.objectContaining({ id: 'k', displayName: 'Kubernetes' }))
    expect(queries).toContain('k8s')
    expect(queries.filter((q) => q === 'k8s')).toHaveLength(1) // debounced: one request, not one per keystroke
  })

  it('hides skills that are already chosen', async () => {
    signIn(userSummary(['RECRUITER']))
    server.use(http.get('*/api/v1/skills', () => HttpResponse.json([JAVA, AWS])))
    renderAt(<SkillPicker onPick={() => {}} excludeIds={[JAVA.id]} />)

    await userEvent.click(screen.getByRole('combobox'))

    expect(await screen.findByRole('button', { name: /AWS/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Java/ })).not.toBeInTheDocument()
  })

  it('says when nothing matches', async () => {
    signIn(userSummary(['RECRUITER']))
    server.use(http.get('*/api/v1/skills', () => HttpResponse.json([])))
    renderAt(<SkillPicker onPick={() => {}} />)

    await userEvent.click(screen.getByRole('combobox'))

    expect(await screen.findByText('No matching skills')).toBeInTheDocument()
  })
})
