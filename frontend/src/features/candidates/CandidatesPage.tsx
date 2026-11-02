import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Badge, Button, Card, Pagination, SelectField, Spinner, TextField } from '../../components/ui'
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
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Candidates</h1>
        <Link
          to="/candidates/new"
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          New candidate
        </Link>
      </div>

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

      <Card>
        {isPending && <Spinner />}
        {isError && <Alert>{error instanceof Error ? error.message : 'Could not load candidates.'}</Alert>}
        {data && data.content.length === 0 && <p className="py-6 text-center text-slate-500">No candidates match your search.</p>}
        {data && data.content.length > 0 && (
          <>
            <p className="mb-3 text-sm text-slate-600" aria-live="polite">
              {data.totalElements} candidate{data.totalElements === 1 ? '' : 's'}
            </p>
            <div className={`overflow-x-auto ${isFetching ? 'opacity-60' : ''}`}>
              <table className="min-w-full text-left text-sm">
                <thead className="border-b border-slate-200 text-slate-500">
                  <tr>
                    <th className="py-2 pr-4 font-medium">Name</th>
                    <th className="py-2 pr-4 font-medium">Title</th>
                    <th className="py-2 pr-4 font-medium">Location</th>
                    <th className="py-2 pr-4 font-medium">Years</th>
                    <th className="py-2 pr-4 font-medium">Skills</th>
                    <th className="py-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.content.map((c) => (
                    <tr key={c.id}>
                      <td className="py-2 pr-4">
                        <Link to={`/candidates/${c.id}`} className="font-medium text-indigo-700 hover:underline">
                          {c.fullName}
                        </Link>
                        <div className="text-xs text-slate-500">{c.email}</div>
                      </td>
                      <td className="py-2 pr-4">{c.currentTitle ?? '—'}</td>
                      <td className="py-2 pr-4">{c.location ?? '—'}</td>
                      <td className="py-2 pr-4">{c.yearsExperience ?? '—'}</td>
                      <td className="py-2 pr-4">{c.skills.slice(0, 4).join(', ')}{c.skills.length > 4 ? ` +${c.skills.length - 4}` : ''}</td>
                      <td className="py-2">
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
