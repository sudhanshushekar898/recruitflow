import { Building2, Plus, SearchX } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Avatar, Badge, Button, Card, EmptyState, PageHeader, Pagination, SelectField, Spinner, TextField, linkButton, table } from '../../components/ui'
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
      <PageHeader
        title="Jobs"
        subtitle="Every role you are hiring for, across all clients"
        actions={
          <>
            <Link to="/clients" className={linkButton('secondary')}><Building2 className="h-4 w-4" aria-hidden />Clients</Link>
            <Link to="/jobs/new" className={linkButton()}><Plus className="h-4 w-4" aria-hidden />New job</Link>
          </>
        }
      />

      <Card>
        <form
          role="search"
          className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:items-end"
          onSubmit={(e) => {
            e.preventDefault()
            setApplied({ ...draft, page: 0 })
          }}
        >
          <TextField label="Keyword" value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} placeholder="Title or location" />
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
            <Button type="button" variant="ghost" onClick={() => { setDraft(EMPTY); setApplied(EMPTY) }}>Clear</Button>
          </div>
        </form>
      </Card>

      <Card padded={false}>
        {isPending && <Spinner />}
        {isError && <div className="p-5"><Alert>{error instanceof Error ? error.message : 'Could not load jobs.'}</Alert></div>}
        {data && data.content.length === 0 && <EmptyState icon={SearchX} title="No jobs match your search." />}
        {data && data.content.length > 0 && (
          <div className={`${table.wrap} ${isFetching ? 'opacity-60' : ''}`}>
            <table className={table.table}>
              <thead className={table.head}>
                <tr>
                  <th className={table.th}>Job</th>
                  <th className={table.th}>Location</th>
                  <th className={table.th}>Type</th>
                  <th className={table.th}>Openings</th>
                  <th className={table.th}>Owner</th>
                  <th className={table.th}>Status</th>
                </tr>
              </thead>
              <tbody className={table.body}>
                {data.content.map((j) => (
                  <tr key={j.id} className={table.row}>
                    <td className={table.td}>
                      <div className="flex items-center gap-3">
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
                          <Building2 className="h-4 w-4" aria-hidden />
                        </span>
                        <div>
                          <Link to={`/jobs/${j.id}`} className="font-semibold text-slate-900 hover:text-indigo-600">{j.title}</Link>
                          <div className="text-xs text-slate-500">{j.clientName}</div>
                        </div>
                      </div>
                    </td>
                    <td className={`${table.td} text-slate-600`}>{j.location ?? '—'}</td>
                    <td className={`${table.td} text-slate-600`}>{EMPLOYMENT[j.employmentType]}</td>
                    <td className={`${table.td} tabular-nums text-slate-600`}>{j.openings}</td>
                    <td className={table.td}>
                      <div className="flex items-center gap-2 text-slate-700"><Avatar name={j.ownerName} size="sm" />{j.ownerName}</div>
                    </td>
                    <td className={table.td}><Badge tone={JOB_TONE[j.status]}>{JOB_STATUSES[j.status]}</Badge></td>
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
