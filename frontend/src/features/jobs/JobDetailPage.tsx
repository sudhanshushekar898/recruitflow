import { Link, useParams } from 'react-router-dom'
import { Alert, Badge, Button, Card, Spinner } from '../../components/ui'
import { useJob, useJobMutations, usePipeline } from './api'
import { EMPLOYMENT, IMPORTANCE, JOB_ACTIONS, JOB_STATUSES, JOB_TONE, STAGE_LABEL, money } from './labels'

export function JobDetailPage() {
  const { id = '' } = useParams()
  const { data: job, isPending, isError } = useJob(id)
  const { data: board } = usePipeline(id)
  const { changeStatus } = useJobMutations()

  if (isPending) return <Spinner />
  if (isError || !job) return <Alert>This job could not be loaded.</Alert>

  return (
    <div className="space-y-6">
      <Link to="/jobs" className="text-sm text-indigo-700 hover:underline">← All jobs</Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">{job.title}</h1>
            <Badge tone={JOB_TONE[job.status]}>{JOB_STATUSES[job.status]}</Badge>
          </div>
          <p className="text-sm text-slate-600">{job.clientName}{job.location ? ` · ${job.location}` : ''}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to={`/jobs/${job.id}/pipeline`} className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700">Pipeline board</Link>
          {job.canEdit && <Link to={`/jobs/${job.id}/edit`} className="rounded-md px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">Edit</Link>}
          {job.canEdit && job.allowedStatuses.map((s) => (
            <Button key={s} variant="secondary" loading={changeStatus.isPending} onClick={() => changeStatus.mutate({ id: job.id, toStatus: s })}>{JOB_ACTIONS[s]}</Button>
          ))}
        </div>
      </div>
      {changeStatus.isError && <Alert>{changeStatus.error.message}</Alert>}

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card title="About the role">
            <dl className="mb-4 grid gap-4 sm:grid-cols-2">
              <Field label="Type">{EMPLOYMENT[job.employmentType]}</Field>
              <Field label="Openings">{String(job.openings)}</Field>
              <Field label="Salary">{money(job.salaryMin, job.salaryMax, job.currency)}</Field>
              <Field label="Owner">{job.ownerName}</Field>
            </dl>
            <p className="whitespace-pre-wrap text-sm text-slate-700">{job.description ?? 'No description yet.'}</p>
          </Card>
          <Card title="Skills">
            {job.skills.length === 0 ? <p className="text-sm text-slate-500">No skills listed.</p> : (
              <ul className="flex flex-wrap gap-2">
                {job.skills.map((s) => (
                  <li key={s.skillId} className={`rounded-full px-3 py-1 text-sm ${s.importance === 'REQUIRED' ? 'bg-indigo-100 text-indigo-800' : 'bg-slate-100 text-slate-700'}`}>
                    {s.displayName}{s.minYears !== undefined ? ` · ${s.minYears}+ yrs` : ''} <span className="text-xs opacity-70">({IMPORTANCE[s.importance]})</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
        <Card title="Pipeline">
          {!board ? <Spinner /> : (
            <>
              <p className="mb-3 text-sm text-slate-600">{board.hired} of {board.openings} openings filled</p>
              <ul className="space-y-1 text-sm">
                {board.stages.map((s) => (
                  <li key={s.status} className="flex justify-between"><span>{STAGE_LABEL[s.status]}</span><span className="font-medium">{s.count}</span></li>
                ))}
              </ul>
            </>
          )}
        </Card>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-sm">{children || '—'}</dd>
    </div>
  )
}
