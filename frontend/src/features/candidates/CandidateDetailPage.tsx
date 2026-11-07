import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert, Avatar, Badge, Button, Card, SelectField, Spinner, TextAreaField, TextField } from '../../components/ui'
import { isApiError } from '../../lib/apiClient'
import type { CandidateView, LegalBasis } from '../../types'
import { useCandidate, useCandidateMutations, useNotes } from './api'
import { useCandidateApplications, useJobMutations, useOpenJobs } from '../jobs/api'
import { STAGE_LABEL, STAGE_TONE } from '../jobs/labels'
import { LEGAL_BASES, LEVELS, SOURCES, STATUSES, formatDate, formatDateTime } from './labels'

export function CandidateDetailPage() {
  const { id = '' } = useParams()
  const { data: candidate, isPending, isError, error } = useCandidate(id)

  if (isPending) return <Spinner />
  if (isError || !candidate) {
    return isApiError(error, 'RESOURCE_NOT_FOUND') ? <Alert>This candidate does not exist.</Alert> : <Alert>Could not load the candidate.</Alert>
  }

  return (
    <div className="space-y-6">
      <Header candidate={candidate} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Profile candidate={candidate} />
          <Applications candidate={candidate} />
          <Notes candidateId={candidate.id} />
        </div>
        <div className="space-y-6">
          <Consent candidate={candidate} />
        </div>
      </div>
    </div>
  )
}

function Header({ candidate }: { candidate: CandidateView }) {
  const { setArchived } = useCandidateMutations()
  const [confirming, setConfirming] = useState(false)
  const archived = candidate.status === 'ARCHIVED'

  return (
    <div className="space-y-3">
      <Link to="/candidates" className="text-sm text-indigo-700 hover:underline">← All candidates</Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-4">
          <Avatar name={`${candidate.firstName} ${candidate.lastName}`} size="lg" />
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold tracking-tight">{candidate.firstName} {candidate.lastName}</h1>
              <Badge tone={candidate.status === 'ACTIVE' ? 'green' : archived ? 'neutral' : 'amber'}>{STATUSES[candidate.status]}</Badge>
            </div>
            <p className="text-sm text-slate-500">{[candidate.currentTitle, candidate.location].filter(Boolean).join(' · ')}</p>
          </div>
        </div>
        <div className="flex gap-2">
          {!archived && (
            <Link to={`/candidates/${candidate.id}/edit`} className="rounded-md px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">
              Edit
            </Link>
          )}
          {archived ? (
            <Button variant="secondary" loading={setArchived.isPending} onClick={() => setArchived.mutate({ id: candidate.id, archived: false })}>
              Restore
            </Button>
          ) : confirming ? (
            <>
              <Button loading={setArchived.isPending} onClick={() => setArchived.mutate({ id: candidate.id, archived: true }, { onSuccess: () => setConfirming(false) })}>
                Confirm archive
              </Button>
              <Button variant="secondary" onClick={() => setConfirming(false)}>Keep</Button>
            </>
          ) : (
            <Button variant="secondary" onClick={() => setConfirming(true)}>Archive</Button>
          )}
        </div>
      </div>
      {setArchived.isError && <Alert>{setArchived.error.message}</Alert>}
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

function Profile({ candidate: c }: { candidate: CandidateView }) {
  return (
    <Card title="Profile">
      <dl className="grid gap-4 sm:grid-cols-2">
        <Field label="Email">{c.email}</Field>
        <Field label="Phone">{c.phone}</Field>
        <Field label="Title">{c.currentTitle}</Field>
        <Field label="Company">{c.currentCompany}</Field>
        <Field label="Location">{c.location}</Field>
        <Field label="Experience">{c.yearsExperience === undefined ? '' : `${c.yearsExperience} years`}</Field>
        <Field label="Source">{SOURCES[c.source]}</Field>
        <Field label="Owner">{c.ownerName}</Field>
        <Field label="LinkedIn">
          {c.linkedinUrl && <a href={c.linkedinUrl} target="_blank" rel="noreferrer noopener" className="text-indigo-700 hover:underline">{c.linkedinUrl}</a>}
        </Field>
        <Field label="Added">{formatDate(c.createdAt)}</Field>
      </dl>
      {c.summary && <p className="mt-4 whitespace-pre-wrap text-sm text-slate-700">{c.summary}</p>}
      <h3 className="mb-2 mt-6 text-sm font-semibold">Skills</h3>
      {c.skills.length === 0 ? (
        <p className="text-sm text-slate-500">No skills recorded.</p>
      ) : (
        <ul className="flex flex-wrap gap-2">
          {c.skills.map((s) => (
            <li key={s.skillId} className="rounded-full bg-indigo-50 px-3 py-1 text-sm text-indigo-800">
              {s.displayName}
              {(s.years !== undefined || s.level) && (
                <span className="text-indigo-600">
                  {' · '}
                  {[s.years !== undefined ? `${s.years}y` : null, s.level ? LEVELS[s.level] : null].filter(Boolean).join(', ')}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function Applications({ candidate }: { candidate: CandidateView }) {
  const { data: applications, isPending } = useCandidateApplications(candidate.id)
  const { data: openJobs } = useOpenJobs()
  const { apply } = useJobMutations()
  const [jobId, setJobId] = useState('')
  const appliedJobIds = new Set(applications?.map((a) => a.jobId))
  const available = openJobs?.content.filter((j) => !appliedJobIds.has(j.id)) ?? []

  return (
    <Card title="Applications">
      {isPending && <Spinner />}
      {applications && applications.length === 0 && <p className="text-sm text-slate-500">Not in any job pipeline yet.</p>}
      <ul className="divide-y divide-slate-100">
        {applications?.map((a) => (
          <li key={a.id} className="flex items-center justify-between py-2 text-sm">
            <span>
              <Link to={`/jobs/${a.jobId}/pipeline`} className="font-medium text-indigo-700 hover:underline">{a.jobTitle}</Link>
              <span className="text-slate-500"> · {a.clientName}</span>
            </span>
            <Badge tone={STAGE_TONE[a.status]}>{STAGE_LABEL[a.status]}</Badge>
          </li>
        ))}
      </ul>
      {candidate.status === 'ACTIVE' && (
        <form
          className="mt-4 flex items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            if (jobId) apply.mutate({ candidateId: candidate.id, jobId }, { onSuccess: () => setJobId('') })
          }}
        >
          <SelectField label="Add to a job" className="flex-1" value={jobId} onChange={(e) => setJobId(e.target.value)}>
            <option value="">{available.length ? 'Choose an open job…' : 'No open jobs available'}</option>
            {available.map((j) => <option key={j.id} value={j.id}>{j.title} · {j.clientName}</option>)}
          </SelectField>
          <Button type="submit" loading={apply.isPending} disabled={!jobId}>Add</Button>
        </form>
      )}
      {apply.isError && <div className="mt-3"><Alert>{apply.error.message}</Alert></div>}
    </Card>
  )
}

function Notes({ candidateId }: { candidateId: string }) {
  const { data: notes, isPending } = useNotes(candidateId)
  const { addNote } = useCandidateMutations()
  const [body, setBody] = useState('')

  return (
    <Card title="Notes">
      <form
        className="space-y-2"
        onSubmit={(event) => {
          event.preventDefault()
          if (body.trim()) addNote.mutate({ id: candidateId, body: body.trim() }, { onSuccess: () => setBody('') })
        }}
      >
        <TextAreaField label="Add a note" value={body} onChange={(e) => setBody(e.target.value)} maxLength={4000} />
        {addNote.isError && <Alert>{addNote.error.message}</Alert>}
        <Button type="submit" loading={addNote.isPending} disabled={!body.trim()}>Add note</Button>
      </form>
      <div className="mt-6">
        {isPending && <Spinner />}
        {notes && notes.length === 0 && <p className="text-sm text-slate-500">No notes yet.</p>}
        <ul className="space-y-4">
          {notes?.map((n) => (
            <li key={n.id} className="border-l-2 border-slate-200 pl-3">
              <p className="whitespace-pre-wrap text-sm">{n.body}</p>
              <p className="mt-1 text-xs text-slate-500">{n.authorName} · {formatDateTime(n.createdAt)}</p>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  )
}

function Consent({ candidate }: { candidate: CandidateView }) {
  const { recordConsent } = useCandidateMutations()
  const consent = candidate.consent
  const [editing, setEditing] = useState(false)
  const [basis, setBasis] = useState<LegalBasis>(consent?.legalBasis ?? 'CONSENT')
  const [givenAt, setGivenAt] = useState(consent?.givenAt.slice(0, 10) ?? '')
  const [expiresAt, setExpiresAt] = useState(consent?.expiresAt?.slice(0, 10) ?? '')
  const [evidence, setEvidence] = useState(consent?.evidence ?? '')
  const [withdrawn, setWithdrawn] = useState(Boolean(consent?.withdrawnAt))

  return (
    <Card title="Consent and retention">
      {!editing && (
        <>
          <dl className="space-y-3">
            <Field label="Legal basis">{consent ? LEGAL_BASES[consent.legalBasis] : ''}</Field>
            <Field label="Given">{consent ? formatDate(consent.givenAt) : ''}</Field>
            <Field label="Evidence">{consent?.evidence}</Field>
            <Field label="Keep data until">{formatDate(candidate.retentionUntil)}</Field>
          </dl>
          {consent?.withdrawnAt && <div className="mt-3"><Alert tone="info">Consent withdrawn on {formatDate(consent.withdrawnAt)}.</Alert></div>}
          {!consent && <p className="mt-3 text-sm text-slate-500">No lawful basis recorded yet.</p>}
          <Button variant="secondary" className="mt-4" onClick={() => setEditing(true)}>{consent ? 'Update' : 'Record'} consent</Button>
        </>
      )}
      {editing && (
        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault()
            if (!givenAt) return
            recordConsent.mutate(
              {
                id: candidate.id,
                consent: {
                  legalBasis: basis,
                  givenAt: `${givenAt}T00:00:00Z`,
                  expiresAt: expiresAt ? `${expiresAt}T00:00:00Z` : undefined,
                  evidence: evidence || undefined,
                  withdrawn,
                },
              },
              { onSuccess: () => setEditing(false) },
            )
          }}
        >
          <SelectField label="Legal basis" value={basis} onChange={(e) => setBasis(e.target.value as LegalBasis)}>
            {Object.entries(LEGAL_BASES).map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </SelectField>
          <TextField label="Date given" type="date" required value={givenAt} onChange={(e) => setGivenAt(e.target.value)} />
          <TextField label="Expires (optional)" type="date" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
          <TextField label="Evidence (optional)" value={evidence} onChange={(e) => setEvidence(e.target.value)} maxLength={300} />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={withdrawn} onChange={(e) => setWithdrawn(e.target.checked)} />
            Consent has been withdrawn
          </label>
          {recordConsent.isError && <Alert>{recordConsent.error.message}</Alert>}
          <div className="flex gap-2">
            <Button type="submit" loading={recordConsent.isPending}>Save</Button>
            <Button type="button" variant="secondary" onClick={() => setEditing(false)}>Cancel</Button>
          </div>
        </form>
      )}
    </Card>
  )
}
