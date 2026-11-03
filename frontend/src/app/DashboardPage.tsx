import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Badge, Card, Spinner } from '../components/ui'
import { useAuth } from '../features/auth/useAuth'
import { useRecentApplications } from '../features/jobs/api'
import { STAGE_LABEL, STAGE_TONE, daysSince } from '../features/jobs/labels'
import { api } from '../lib/apiClient'
import type { PageResponse } from '../types'

function useTotal(path: string, query: Record<string, string | number>) {
  return useQuery({
    queryKey: ['dashboard', path, query],
    queryFn: ({ signal }) => api<PageResponse<unknown>>(path, { query: { ...query, size: 1 }, signal }),
    select: (page) => page.totalElements,
  })
}

/** A first look at the agency's activity; proper analytics (funnel, time to hire) arrive in a later milestone. */
export function DashboardPage() {
  const { user, hasAnyRole } = useAuth()
  const recruiter = hasAnyRole('ADMIN', 'RECRUITER')
  const firstName = user?.name.split(' ')[0]
  const openJobs = useTotal('/api/v1/jobs', { status: 'OPEN' })
  const candidates = useTotal('/api/v1/candidates', { status: 'ACTIVE' })
  const inInterview = useTotal('/api/v1/applications', { status: 'INTERVIEW' })
  const offers = useTotal('/api/v1/applications', { status: 'OFFER' })
  const recent = useRecentApplications()

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Welcome{firstName ? `, ${firstName}` : ''}</h1>
      {!recruiter && (
        <Card><p className="text-slate-600">Your interviews and feedback will appear here once interview scheduling is built.</p></Card>
      )}
      {recruiter && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Open jobs" value={openJobs.data} to="/jobs" />
            <Stat label="Active candidates" value={candidates.data} to="/candidates" />
            <Stat label="In interview" value={inInterview.data} />
            <Stat label="Offers out" value={offers.data} />
          </div>
          <Card title="Latest pipeline activity">
            {recent.isPending && <Spinner />}
            <ul className="divide-y divide-slate-100">
              {recent.data?.content.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                  <span>
                    <Link to={`/candidates/${a.candidateId}`} className="font-medium text-indigo-700 hover:underline">{a.candidateName}</Link>
                    {' '}for <Link to={`/jobs/${a.jobId}/pipeline`} className="text-indigo-700 hover:underline">{a.jobTitle}</Link>
                    <span className="text-slate-500"> · {a.clientName}</span>
                  </span>
                  <span className="flex items-center gap-2 text-xs text-slate-500">
                    {daysSince(a.statusChangedAt)}d ago <Badge tone={STAGE_TONE[a.status]}>{STAGE_LABEL[a.status]}</Badge>
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        </>
      )}
    </div>
  )
}

function Stat({ label, value, to }: { label: string; value?: number; to?: string }) {
  const body = (
    <div className="rounded-lg bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <p className="text-3xl font-semibold">{value ?? '–'}</p>
      <p className="mt-1 text-sm text-slate-600">{label}</p>
    </div>
  )
  return to ? <Link to={to} className="block hover:opacity-90">{body}</Link> : body
}

export function NotFoundPage() {
  return (
    <div className="py-16 text-center">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-slate-600">The page you are looking for does not exist.</p>
    </div>
  )
}
