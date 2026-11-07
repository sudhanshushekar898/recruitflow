import { useState } from 'react'
import { Link } from 'react-router-dom'
import { SearchX, UserPlus } from 'lucide-react'
import { Alert, Avatar, Badge, Button, Card, EmptyState, PageHeader, Pagination, SelectField, Spinner, TextField, linkButton, table } from '../../components/ui'
import type { CandidateSource, CandidateStatus } from '../../types'
import { SkillPicker } from '../skills/SkillPicker'
import { type CandidateFilters, useCandidates } from './api'
import { SOURCES, STATUSES } from './labels'

const EMPTY: CandidateFilters = { q: '', location: '', minYears: '', source: '', status: '', skillIds: [], page: 0 }

export function CandidatesPage() {
  // `draft` is what the form shows; `applied` is what was searched, so typing does not fire a request per key.
  const [draft, setDraft] = useState<CandidateFilters>(EMPTY)
  const [applied, setApplied] = useState<CandidateFilters>(EMPTY)
  const [skillNames, setSkillNames] = useState<Record<string, string>>({})
  const { data, isPending, isError, error, isFetching } = useCandidates(applied)

  const set = <K extends keyof CandidateFilters>(key: K, value: CandidateFilters[K]) => setDraft((d) => ({ ...d, [key]: value }))

  return (
    <div className="space-y-6">
      <PageHeader
        title="Candidates"
        subtitle="Search the talent pool by keyword, skills, location and experience"
        actions={<Link to="/candidates/new" className={linkButton()}><UserPlus className="h-4 w-4" aria-hidden />New candidate</Link>}
      />

      <Card>
        <form
          className="space-y-4"
          role="search"
          onSubmit={(event) => {
            event.preventDefault()
            setApplied({ ...draft, page: 0 })
          }}
        >
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <TextField label="Keyword" value={draft.q} onChange={(e) => set('q', e.target.value)} hint="Name, email, title, company" />
            <TextField label="Location" value={draft.location} onChange={(e) => set('location', e.target.value)} />
            <TextField
              label="Minimum years"
              type="number"
              min={0}
              max={60}
              value={draft.minYears}
              onChange={(e) => set('minYears', e.target.value)}
            />
            <SelectField label="Source" value={draft.source} onChange={(e) => set('source', e.target.value as CandidateSource | '')}>
              <option value="">Any source</option>
              {Object.entries(SOURCES).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </SelectField>
            <SelectField label="Status" value={draft.status} onChange={(e) => set('status', e.target.value as CandidateStatus | '')}>
              <option value="">Active and do-not-contact</option>
              <option value="ARCHIVED">Archived</option>
              <option value="ACTIVE">Active only</option>
              <option value="DO_NOT_CONTACT">Do not contact</option>
            </SelectField>
            <div className="sm:col-span-2">
              <SkillPicker
                label="Must have skills"
                excludeIds={draft.skillIds}
                onPick={(skill) => {
                  setSkillNames((names) => ({ ...names, [skill.id]: skill.displayName }))
                  set('skillIds', [...draft.skillIds, skill.id])
                }}
              />
            </div>
          </div>

          {draft.skillIds.length > 0 && (
            <ul className="flex flex-wrap gap-2" aria-label="Selected skills">
              {draft.skillIds.map((id) => (
                <li key={id} className="flex items-center gap-1 rounded-full bg-indigo-50 px-3 py-1 text-sm text-indigo-800">
                  {skillNames[id] ?? 'Skill'}
                  <button
                    type="button"
                    aria-label={`Remove ${skillNames[id] ?? 'skill'} filter`}
                    onClick={() => set('skillIds', draft.skillIds.filter((s) => s !== id))}
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
          )}

          <div className="flex gap-2">
            <Button type="submit">Search</Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setDraft(EMPTY)
                setApplied(EMPTY)
                setSkillNames({})
              }}
            >
              Clear
            </Button>
          </div>
        </form>
      </Card>

      <Card padded={false}>
        {isPending && <Spinner />}
        {isError && <div className="p-5"><Alert>{error instanceof Error ? error.message : 'Could not load candidates.'}</Alert></div>}
        {data && data.content.length === 0 && (
          <EmptyState icon={SearchX} title="No candidates match your search.">Try fewer filters or a different keyword.</EmptyState>
        )}
        {data && data.content.length > 0 && (
          <>
            <p className="border-b border-slate-100 px-5 py-3 text-sm text-slate-500" aria-live="polite">
              <span className="font-semibold text-slate-900">{data.totalElements}</span> candidate{data.totalElements === 1 ? '' : 's'}
            </p>
            <div className={`${table.wrap} ${isFetching ? 'opacity-60' : ''}`}>
              <table className={table.table}>
                <thead className={table.head}>
                  <tr>
                    <th className={table.th}>Name</th>
                    <th className={table.th}>Title</th>
                    <th className={table.th}>Location</th>
                    <th className={table.th}>Years</th>
                    <th className={table.th}>Skills</th>
                    <th className={table.th}>Status</th>
                  </tr>
                </thead>
                <tbody className={table.body}>
                  {data.content.map((c) => (
                    <tr key={c.id} className={table.row}>
                      <td className={table.td}>
                        <div className="flex items-center gap-3">
                          <Avatar name={c.fullName} />
                          <div>
                            <Link to={`/candidates/${c.id}`} className="font-semibold text-slate-900 hover:text-indigo-600">{c.fullName}</Link>
                            <div className="text-xs text-slate-500">{c.email}</div>
                          </div>
                        </div>
                      </td>
                      <td className={`${table.td} text-slate-700`}>{c.currentTitle ?? '—'}</td>
                      <td className={`${table.td} text-slate-600`}>{c.location ?? '—'}</td>
                      <td className={`${table.td} tabular-nums text-slate-600`}>{c.yearsExperience ?? '—'}</td>
                      <td className={table.td}>
                        <div className="flex max-w-xs flex-wrap gap-1">
                          {c.skills.slice(0, 3).map((s) => <span key={s} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-xs text-slate-700">{s}</span>)}
                          {c.skills.length > 3 && <span className="px-1 text-xs text-slate-400">+{c.skills.length - 3}</span>}
                        </div>
                      </td>
                      <td className={table.td}>
                        <Badge tone={c.status === 'ACTIVE' ? 'green' : c.status === 'ARCHIVED' ? 'neutral' : 'amber'}>{STATUSES[c.status]}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={(page) => setApplied({ ...applied, page })} />}
      </Card>
    </div>
  )
}
