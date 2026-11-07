import { useQueries, useQuery } from '@tanstack/react-query'
import { Activity, Briefcase, CalendarClock, FileSignature, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Avatar, Badge, Card, EmptyState, PageHeader, Spinner, StatCard } from '../components/ui'
import { useAuth } from '../features/auth/useAuth'
import { useRecentApplications } from '../features/jobs/api'
import { STAGE_LABEL, STAGE_TONE, daysSince } from '../features/jobs/labels'
import { api } from '../lib/apiClient'
import type { ApplicationStatus, PageResponse } from '../types'

const FUNNEL: ApplicationStatus[] = ['NEW', 'SCREENING', 'SHORTLISTED', 'INTERVIEW', 'OFFER', 'HIRED']
const BAR = ['bg-slate-400', 'bg-sky-500', 'bg-indigo-500', 'bg-violet-500', 'bg-amber-500', 'bg-emerald-500']

function total(path: string, query: Record<string, string | number>) {
  return {
    queryKey: ['dashboard', path, query],
    queryFn: ({ signal }: { signal: AbortSignal }) => api<PageResponse<unknown>>(path, { query: { ...query, size: 1 }, signal }),
    select: (page: PageResponse<unknown>) => page.totalElements,
  }
}

export function DashboardPage() {
  const { user, hasAnyRole } = useAuth()
  const recruiter = hasAnyRole('ADMIN', 'RECRUITER')
  const firstName = user?.name.split(' ')[0]
  const today = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())

  return (
    <div className="space-y-8">
      <PageHeader title={`Welcome${firstName ? `, ${firstName}` : ''}`} subtitle={today} />
      {recruiter ? (
        <RecruiterDashboard />
      ) : (
        <Card>
          <EmptyState icon={CalendarClock} title="Your interviews will appear here">
            Once a recruiter adds you to an interview panel, you will see it here and give your feedback.
          </EmptyState>
        </Card>
      )}
    </div>
  )
}

function RecruiterDashboard() {
  const openJobs = useQuery(total('/api/v1/jobs', { status: 'OPEN' }))
  const candidates = useQuery(total('/api/v1/candidates', { status: 'ACTIVE' }))
  const funnel = useQueries({ queries: FUNNEL.map((status) => total('/api/v1/applications', { status })) })
  const recent = useRecentApplications()
  const counts = funnel.map((q) => q.data ?? 0)
  const max = Math.max(1, ...counts)

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Link to="/jobs"><StatCard label="Open jobs" value={openJobs.data} icon={Briefcase} /></Link>
        <Link to="/candidates"><StatCard label="Active candidates" value={candidates.data} icon={Users} tone="emerald" /></Link>
        <StatCard label="In interview" value={funnel[3]?.data} icon={CalendarClock} tone="violet" />
        <StatCard label="Offers out" value={funnel[4]?.data} icon={FileSignature} tone="amber" />
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        <div className="lg:col-span-2">
          <Card title="Hiring funnel" description="Where every live application stands">
            <ul className="space-y-3.5">
              {FUNNEL.map((stage, i) => (
                <li key={stage}>
                  <div className="mb-1 flex justify-between text-sm">
                    <span className="font-medium text-slate-700">{STAGE_LABEL[stage]}</span>
                    <span className="tabular-nums text-slate-500">{funnel[i]?.data ?? '–'}</span>
                  </div>
                  <div className="h-2.5 overflow-hidden rounded-full bg-slate-100">
                    <div className={`h-full rounded-full ${BAR[i]} transition-all duration-700`} style={{ width: `${(counts[i]! / max) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        </div>

        <div className="lg:col-span-3">
          <Card title="Latest pipeline activity" action={<Activity className="h-4 w-4 text-slate-400" aria-hidden />} padded={false}>
            {recent.isPending && <Spinner />}
            {recent.data && recent.data.content.length === 0 && <EmptyState icon={Activity} title="No activity yet" />}
            <ul className="divide-y divide-slate-100">
              {recent.data?.content.map((a) => (
                <li key={a.id} className="flex items-center gap-3 px-5 py-3">
                  <Avatar name={a.candidateName} size="sm" />
                  <div className="min-w-0 flex-1 text-sm">
                    <Link to={`/candidates/${a.candidateId}`} className="font-medium text-slate-900 hover:text-indigo-600">{a.candidateName}</Link>
                    <p className="truncate text-slate-500">
                      <Link to={`/jobs/${a.jobId}/pipeline`} className="hover:text-indigo-600">{a.jobTitle}</Link> · {a.clientName}
                    </p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <Badge tone={STAGE_TONE[a.status]}>{STAGE_LABEL[a.status]}</Badge>
                    <span className="text-xs text-slate-400">{daysSince(a.statusChangedAt) === 0 ? 'today' : `${daysSince(a.statusChangedAt)}d ago`}</span>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>
    </>
  )
}

export function NotFoundPage() {
  return (
    <div className="py-16 text-center">
      <p className="text-sm font-semibold text-indigo-600">404</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight">Page not found</h1>
      <p className="mt-2 text-slate-600">The page you are looking for does not exist.</p>
      <Link to="/" className="mt-6 inline-block text-sm font-semibold text-indigo-600 hover:text-indigo-500">Back to dashboard →</Link>
    </div>
  )
}
