import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Alert, Button, Card, SelectField, Spinner, TextAreaField, TextField } from '../../components/ui'
import { ApiError } from '../../lib/errors'
import type { EmploymentType, JobView, SkillImportance } from '../../types'
import { SkillPicker } from '../skills/SkillPicker'
import { type SkillRequirement, useClients, useJob, useJobMutations } from './api'
import { EMPLOYMENT, IMPORTANCE } from './labels'

/** Create (/jobs/new) and edit (/jobs/:id/edit) share one form. */
export function JobFormPage() {
  const { id } = useParams()
  const { data: existing, isPending, isError } = useJob(id ?? '')
  if (id && isPending) return <Spinner />
  if (id && (isError || !existing)) return <Alert>This job could not be loaded.</Alert>
  if (existing && !existing.canEdit) return <Alert tone="info">Only the owner of this job or an admin can edit it. <Link to={`/jobs/${existing.id}`} className="underline">Back to the job</Link></Alert>
  return <JobForm key={existing?.id ?? 'new'} existing={existing} />
}

function JobForm({ existing }: { existing?: JobView }) {
  const navigate = useNavigate()
  const { create, update } = useJobMutations()
  const { data: clients } = useClients()
  const [clientId, setClientId] = useState(existing?.clientId ?? '')
  const [title, setTitle] = useState(existing?.title ?? '')
  const [location, setLocation] = useState(existing?.location ?? '')
  const [type, setType] = useState<EmploymentType>(existing?.employmentType ?? 'FULL_TIME')
  const [openings, setOpenings] = useState(String(existing?.openings ?? 1))
  const [salaryMin, setSalaryMin] = useState(existing?.salaryMin?.toString() ?? '')
  const [salaryMax, setSalaryMax] = useState(existing?.salaryMax?.toString() ?? '')
  const [currency, setCurrency] = useState(existing?.currency ?? 'GBP')
  const [description, setDescription] = useState(existing?.description ?? '')
  const [skills, setSkills] = useState<SkillRequirement[]>(
    existing?.skills.map((s) => ({ skillId: s.skillId, displayName: s.displayName, importance: s.importance, minYears: s.minYears, weight: s.weight ?? 3 })) ?? [],
  )
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const saving = create.isPending || update.isPending

  const patchSkill = (skillId: string, patch: Partial<SkillRequirement>) => setSkills(skills.map((s) => (s.skillId === skillId ? { ...s, ...patch } : s)))

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    const errors: Record<string, string> = {}
    if (!clientId) errors.clientId = 'Choose a client'
    if (!title.trim()) errors.title = 'Enter a job title'
    if (!/^\d+$/.test(openings) || Number(openings) < 1) errors.openings = 'At least 1'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    const num = (v: string) => (v.trim() === '' ? undefined : Number(v))
    const job = {
      clientId, title: title.trim(), description: description.trim(), location: location.trim(), employmentType: type,
      salaryMin: num(salaryMin), salaryMax: num(salaryMax), currency: num(salaryMin) !== undefined || num(salaryMax) !== undefined ? currency.trim().toUpperCase() : undefined,
      openings: Number(openings),
    }
    try {
      if (existing) {
        await update.mutateAsync({ id: existing.id, version: existing.version, changes: job, skills })
        navigate(`/jobs/${existing.id}`)
      } else {
        const created = await create.mutateAsync({ job, skills })
        navigate(`/jobs/${created.id}`)
      }
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors.length > 0) setFieldErrors(Object.fromEntries(e.fieldErrors.map((f) => [f.field, f.message])))
      else setError(e instanceof ApiError && e.code === 'STALE_VERSION' ? 'Someone else changed this job. Reload and try again.' : e instanceof Error ? e.message : 'Something went wrong.')
    }
  }

  return (
    <form onSubmit={submit} noValidate className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-semibold">{existing ? `Edit ${existing.title}` : 'New job'}</h1>
      {error && <Alert>{error}</Alert>}
      <Card title="Details">
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Client" value={clientId} onChange={(e) => setClientId(e.target.value)}>
            <option value="">Choose a client…</option>
            {clients?.content.filter((c) => c.active || c.id === clientId).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </SelectField>
          <TextField label="Job title" value={title} onChange={(e) => setTitle(e.target.value)} error={fieldErrors.title} />
          <TextField label="Location" value={location} onChange={(e) => setLocation(e.target.value)} />
          <SelectField label="Employment type" value={type} onChange={(e) => setType(e.target.value as EmploymentType)}>
            {Object.entries(EMPLOYMENT).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </SelectField>
          <TextField label="Openings" inputMode="numeric" value={openings} onChange={(e) => setOpenings(e.target.value)} error={fieldErrors.openings} />
          <TextField label="Currency" value={currency} maxLength={3} onChange={(e) => setCurrency(e.target.value)} error={fieldErrors.currency} />
          <TextField label="Salary from" inputMode="numeric" value={salaryMin} onChange={(e) => setSalaryMin(e.target.value)} />
          <TextField label="Salary to" inputMode="numeric" value={salaryMax} onChange={(e) => setSalaryMax(e.target.value)} error={fieldErrors.salaryMax} />
        </div>
        {fieldErrors.clientId && <p className="mt-2 text-sm text-red-600">{fieldErrors.clientId}</p>}
        <TextAreaField label="Description" className="mt-4" value={description} onChange={(e) => setDescription(e.target.value)} />
      </Card>

      <Card title="Skills this job needs">
        <SkillPicker label="Add a skill" excludeIds={skills.map((s) => s.skillId)}
          onPick={(skill) => setSkills([...skills, { skillId: skill.id, displayName: skill.displayName, importance: 'REQUIRED', weight: 3 }])} />
        {fieldErrors.skills && <p className="mt-2 text-sm text-red-600">{fieldErrors.skills}</p>}
        <ul className="mt-3 space-y-2">
          {skills.map((s) => (
            <li key={s.skillId} className="flex flex-wrap items-center gap-2 rounded-md bg-slate-50 p-2">
              <span className="min-w-24 flex-1 text-sm font-medium">{s.displayName}</span>
              <select aria-label={`Importance of ${s.displayName}`} value={s.importance} onChange={(e) => patchSkill(s.skillId, { importance: e.target.value as SkillImportance })} className="rounded-md border-0 bg-white px-2 py-1 text-sm ring-1 ring-slate-300">
                {Object.entries(IMPORTANCE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
              <input aria-label={`Minimum years of ${s.displayName}`} type="number" min={0} max={60} placeholder="Min yrs" value={s.minYears ?? ''} onChange={(e) => patchSkill(s.skillId, { minYears: e.target.value === '' ? undefined : Number(e.target.value) })} className="w-20 rounded-md border-0 px-2 py-1 text-sm ring-1 ring-slate-300" />
              <select aria-label={`Weight of ${s.displayName}`} value={s.weight} onChange={(e) => patchSkill(s.skillId, { weight: Number(e.target.value) })} className="rounded-md border-0 bg-white px-2 py-1 text-sm ring-1 ring-slate-300">
                {[1, 2, 3, 4, 5].map((w) => <option key={w} value={w}>Weight {w}</option>)}
              </select>
              <button type="button" className="text-sm text-red-600 hover:underline" aria-label={`Remove ${s.displayName}`} onClick={() => setSkills(skills.filter((x) => x.skillId !== s.skillId))}>Remove</button>
            </li>
          ))}
        </ul>
      </Card>

      <div className="flex gap-3">
        <Button type="submit" loading={saving}>{existing ? 'Save changes' : 'Create job'}</Button>
        <Link to={existing ? `/jobs/${existing.id}` : '/jobs'} className="inline-flex items-center rounded-md px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">Cancel</Link>
      </div>
    </form>
  )
}
