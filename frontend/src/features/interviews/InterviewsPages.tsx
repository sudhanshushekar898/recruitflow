import { CalendarCheck, CalendarDays, History } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Card, EmptyState, PageHeader, Spinner } from '../../components/ui'
import type { InterviewView } from '../../types'
import { useInterviews, useMyInterviews } from './api'
import { InterviewCard } from './InterviewCard'

function Tabs({ value, onChange }: { value: 'upcoming' | 'past'; onChange: (v: 'upcoming' | 'past') => void }) {
  return (
    <div role="tablist" className="inline-flex rounded-lg bg-slate-100 p-1">
      {(['upcoming', 'past'] as const).map((tab) => (
        <button
          key={tab}
          role="tab"
          aria-selected={value === tab}
          onClick={() => onChange(tab)}
          className={`rounded-md px-4 py-1.5 text-sm font-semibold capitalize transition ${value === tab ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'}`}
        >
          {tab}
        </button>
      ))}
    </div>
  )
}

function InterviewList({ items, pending, empty }: { items?: InterviewView[]; pending: boolean; empty: string }) {
  return (
    <Card padded={false}>
      {pending && <Spinner />}
      {items && items.length === 0 && <EmptyState icon={CalendarDays} title={empty} />}
      <ul className="divide-y divide-slate-100">
        {items?.map((i) => <li key={i.id}><InterviewCard interview={i} /></li>)}
      </ul>
    </Card>
  )
}

/** Recruiters and admins: every interview across the agency. */
export function InterviewsPage() {
  const [tab, setTab] = useState<'upcoming' | 'past'>('upcoming')
  const now = useMemo(() => new Date().toISOString(), [])
  const filters = tab === 'upcoming'
    ? { from: now, status: 'SCHEDULED' as const, sort: 'scheduledStart,asc' }
    : { to: now, sort: 'scheduledStart,desc' }
  const { data, isPending } = useInterviews(filters)

  return (
    <div className="space-y-6">
      <PageHeader title="Interviews" subtitle="Schedule interviews from a job's pipeline board" actions={<Tabs value={tab} onChange={setTab} />} />
      <InterviewList items={data?.content} pending={isPending} empty={tab === 'upcoming' ? 'No upcoming interviews' : 'No past interviews'} />
    </div>
  )
}

/** Everyone: the interviews you sit on, with feedback you still owe at the top. */
export function MyInterviewsPage() {
  const upcoming = useMyInterviews('upcoming')
  const past = useMyInterviews('past')
  const due = past.data?.filter((i) => i.canGiveFeedback) ?? []
  const done = past.data?.filter((i) => !i.canGiveFeedback) ?? []

  return (
    <div className="space-y-8">
      <PageHeader title="My interviews" subtitle="Interviews where you are on the panel" />
      {due.length > 0 && (
        <section className="space-y-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-amber-700"><CalendarCheck className="h-4 w-4" />Feedback due ({due.length})</h2>
          <InterviewList items={due} pending={false} empty="" />
        </section>
      )}
      <section className="space-y-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500"><CalendarDays className="h-4 w-4" />Upcoming</h2>
        <InterviewList items={upcoming.data} pending={upcoming.isPending} empty="Nothing scheduled for you" />
      </section>
      <section className="space-y-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500"><History className="h-4 w-4" />Past</h2>
        <InterviewList items={done} pending={past.isPending} empty="No past interviews" />
      </section>
    </div>
  )
}
