import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Badge, Button, Card, Pagination, SelectField, Spinner, TextField } from '../../components/ui'
import type { JobStatus } from '../../types'
import { type JobFilters, useClients, useJobs } from './api'
import { EMPLOYMENT, JOB_STATUSES, JOB_TONE } from './labels'

const EMPTY: JobFilters = { q: '', status: '', clientId: '', page: 0 }

export function JobsPage() {
  const [draft, setDraft] = useState<JobFilters>(EMPTY)
  const [applied, setApplied] = useState<JobFilters>(EMPTY)
  const { data, isPending, isError, error, isFetching } = useJobs(applied)
  const { data: clients } = useClients()

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Jobs</h1>
        <div className="flex gap-2">
          <Link to="/clients" className="rounded-md px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">Clients</Link>
          <Link to="/jobs/new" className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700">New job</Link>
        </div>
      </div>

      <Card>
        <form
          role="search"
          className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:items-end"
          onSubmit={(e) => {
            e.preventDefault()
            setApplied({ ...draft, page: 0 })
          }}
        >
          <TextField label="Keyword" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} hint="Title or location" />
          <SelectField label="Status" value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value as JobStatus | '' })}>
            <option value="">Any status</option>
            {Object.entries(JOB_STATUSES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </SelectField>
          <SelectField label="Client" value={draft.clientId} onChange={(e) => setDraft({ ...draft, clientId: e.target.value })}>
            <option value="">Any client</option>
            {clients?.content.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </SelectField>
          <div className="flex gap-2">
            <Button type="submit">Search</Button>
            <Button type="button" variant="secondary" onClick={() => { setDraft(EMPTY); setApplied(EMPTY) }}>Clear</Button>
          </div>
        </form>
      </Card>

      <Card>
        {isPending && <Spinner />}
        {isError && <Alert>{error instanceof Error ? error.message : 'Could not load jobs.'}</Alert>}
        {data && data.content.length === 0 && <p className="py-6 text-center text-slate-500">No jobs match your search.</p>}
        {data && data.content.length > 0 && (
          <div className={`overflow-x-auto ${isFetching ? 'opacity-60' : ''}`}>
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-slate-500">
                <tr>
                  <th className="py-2 pr-4 font-medium">Job</th>
                  <th className="py-2 pr-4 font-medium">Client</th>
                  <th className="py-2 pr-4 font-medium">Location</th>
                  <th className="py-2 pr-4 font-medium">Type</th>
                  <th className="py-2 pr-4 font-medium">Openings</th>
                  <th className="py-2 pr-4 font-medium">Owner</th>
                  <th className="py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((j) => (
                  <tr key={j.id}>
                    <td className="py-2 pr-4"><Link to={`/jobs/${j.id}`} className="font-medium text-indigo-700 hover:underline">{j.title}</Link></td>
                    <td className="py-2 pr-4">{j.clientName}</td>
                    <td className="py-2 pr-4">{j.location ?? '—'}</td>
                    <td className="py-2 pr-4">{EMPLOYMENT[j.employmentType]}</td>
                    <td className="py-2 pr-4">{j.openings}</td>
                    <td className="py-2 pr-4">{j.ownerName}</td>
                    <td className="py-2"><Badge tone={JOB_TONE[j.status]}>{JOB_STATUSES[j.status]}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(page) => setApplied({ ...applied, page })} />}
      </Card>
    </div>
  )
}
