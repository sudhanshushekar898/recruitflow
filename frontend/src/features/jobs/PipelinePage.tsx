import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert, Badge, Button, Card, SelectField, Spinner, TextAreaField, TextField } from '../../components/ui'
import { ApiError } from '../../lib/errors'
import type { ApplicationStatus, ApplicationView, RejectionReason } from '../../types'
import { useCandidates } from '../candidates/api'
import { useJobMutations, usePipeline } from './api'
import { REASONS, STAGE_LABEL, STAGE_TONE, daysSince } from './labels'

const NO_FILTERS = { q: '', location: '', minYears: '', source: '' as const, status: 'ACTIVE' as const, skillIds: [], page: 0 }

/** The hiring board for one job: a column per stage, drag a card (or use its menu) to move it. */
export function PipelinePage() {
  const { id = '' } = useParams()
  const { data: board, isPending, isError } = usePipeline(id)
  const { move } = useJobMutations()
  const [message, setMessage] = useState<{ tone: 'error' | 'success' | 'info'; text: string } | null>(null)
  const [rejecting, setRejecting] = useState<ApplicationView | null>(null)
  const [dragOver, setDragOver] = useState<ApplicationStatus | null>(null)

  if (isPending) return <Spinner />
  if (isError || !board) return <Alert>This job could not be loaded.</Alert>
  const all = board.stages.flatMap((s) => s.applications)

  function request(application: ApplicationView, to: ApplicationStatus) {
    setMessage(null)
    if (!application.allowedNext.includes(to)) {
      setMessage({ tone: 'error', text: `${application.candidateName} cannot move from ${STAGE_LABEL[application.status]} to ${STAGE_LABEL[to]}.` })
      return
    }
    if (to === 'REJECTED') {
      setRejecting(application)
      return
    }
    run(application, to)
  }

  function run(application: ApplicationView, to: ApplicationStatus, reason?: RejectionReason, comment?: string) {
    move.mutate({ application, to, reason, comment }, {
      onSuccess: (result) => {
        setRejecting(null)
        setMessage(result.jobCloseSuggested
          ? { tone: 'success', text: `${application.candidateName} is hired. All openings are now filled: consider closing the job.` }
          : { tone: 'success', text: `${application.candidateName} moved to ${STAGE_LABEL[to]}.` })
      },
      onError: (error) => {
        setRejecting(null)
        setMessage({ tone: 'error', text: error instanceof ApiError && error.code === 'STALE_VERSION'
          ? 'Someone else moved this candidate first. The board has been refreshed.' : error.message })
      },
    })
  }

  return (
    <div className="space-y-4">
      <Link to={`/jobs/${board.jobId}`} className="text-sm text-indigo-700 hover:underline">← {board.jobTitle}</Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{board.jobTitle} pipeline</h1>
          <p className="text-sm text-slate-600">{board.clientName} · {board.hired} of {board.openings} openings filled · {all.length} candidates</p>
        </div>
        <Badge tone={board.jobStatus === 'OPEN' ? 'green' : 'amber'}>{board.jobStatus.replace('_', ' ')}</Badge>
      </div>

      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      {board.jobStatus === 'OPEN' && <AddCandidate jobId={board.jobId} onAdded={(name) => setMessage({ tone: 'success', text: `${name} added to the pipeline.` })} />}

      <div className="flex gap-3 overflow-x-auto pb-4" aria-label="Pipeline board">
        {board.stages.map((stage) => (
          <section
            key={stage.status}
            aria-label={`${STAGE_LABEL[stage.status]} column`}
            onDragOver={(e) => { e.preventDefault(); setDragOver(stage.status) }}
            onDragLeave={() => setDragOver(null)}
            onDrop={(e) => {
              e.preventDefault()
              setDragOver(null)
              const dragged = all.find((a) => a.id === e.dataTransfer.getData('text/plain'))
              if (dragged && dragged.status !== stage.status) request(dragged, stage.status)
            }}
            className={`w-64 shrink-0 rounded-lg p-2 ${dragOver === stage.status ? 'bg-indigo-100' : 'bg-slate-100'}`}
          >
            <h2 className="mb-2 flex items-center justify-between px-1 text-sm font-semibold">
              {STAGE_LABEL[stage.status]} <Badge tone={STAGE_TONE[stage.status]}>{stage.count}</Badge>
            </h2>
            <ul className="space-y-2">
              {stage.applications.map((a) => (
                <li
                  key={a.id}
                  draggable
                  onDragStart={(e) => e.dataTransfer.setData('text/plain', a.id)}
                  className="cursor-grab rounded-md bg-white p-3 text-sm shadow-sm ring-1 ring-slate-200 active:cursor-grabbing"
                >
                  <Link to={`/candidates/${a.candidateId}`} className="font-medium text-indigo-700 hover:underline">{a.candidateName}</Link>
                  <p className="text-xs text-slate-500">{a.candidateTitle ?? '—'}</p>
                  <p className="mt-1 text-xs text-slate-500">{a.ownerName} · {daysSince(a.statusChangedAt)}d in stage</p>
                  {a.rejectionReason && <p className="mt-1 text-xs text-red-700">{REASONS[a.rejectionReason]}</p>}
                  {a.allowedNext.length > 0 && (
                    <select
                      aria-label={`Move ${a.candidateName}`}
                      value=""
                      onChange={(e) => e.target.value && request(a, e.target.value as ApplicationStatus)}
                      className="mt-2 w-full rounded-md border-0 bg-slate-50 px-2 py-1 text-xs ring-1 ring-slate-200"
                    >
                      <option value="">Move to…</option>
                      {a.allowedNext.map((s) => <option key={s} value={s}>{STAGE_LABEL[s]}</option>)}
                    </select>
                  )}
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>

      {rejecting && <RejectDialog application={rejecting} busy={move.isPending} onCancel={() => setRejecting(null)}
        onConfirm={(reason, comment) => run(rejecting, 'REJECTED', reason, comment)} />}
    </div>
  )
}

function RejectDialog({ application, busy, onCancel, onConfirm }: {
  application: ApplicationView; busy: boolean; onCancel: () => void; onConfirm: (reason: RejectionReason, comment: string) => void
}) {
  const [reason, setReason] = useState<RejectionReason | ''>('')
  const [comment, setComment] = useState('')
  return (
    <div role="dialog" aria-modal="true" aria-label={`Reject ${application.candidateName}`} className="fixed inset-0 z-20 flex items-center justify-center bg-black/40 p-4">
      <form
        className="w-full max-w-md space-y-4 rounded-lg bg-white p-6 shadow-xl"
        onSubmit={(e) => { e.preventDefault(); if (reason) onConfirm(reason, comment) }}
      >
        <h2 className="text-lg font-semibold">Reject {application.candidateName}</h2>
        <SelectField label="Reason" value={reason} onChange={(e) => setReason(e.target.value as RejectionReason)}>
          <option value="">Choose a reason…</option>
          {Object.entries(REASONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </SelectField>
        <TextAreaField label="Comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} />
        <div className="flex gap-2">
          <Button type="submit" loading={busy} disabled={!reason}>Reject</Button>
          <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
        </div>
      </form>
    </div>
  )
}

function AddCandidate({ jobId, onAdded }: { jobId: string; onAdded: (name: string) => void }) {
  const [q, setQ] = useState('')
  const [applied, setApplied] = useState('')
  const [open, setOpen] = useState(false)
  const { data } = useCandidates({ ...NO_FILTERS, q: applied })
  const { apply } = useJobMutations()
  if (!open) return <Button variant="secondary" onClick={() => setOpen(true)}>Add candidate</Button>
  return (
    <Card title="Add a candidate to this job">
      <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); setApplied(q.trim()) }}>
        <TextField label="Search candidates" className="flex-1" value={q} onChange={(e) => setQ(e.target.value)} hint="Name, title, location" />
        <Button type="submit" className="self-end">Search</Button>
        <Button type="button" variant="secondary" className="self-end" onClick={() => setOpen(false)}>Close</Button>
      </form>
      {apply.isError && <div className="mt-3"><Alert>{apply.error instanceof ApiError && apply.error.code === 'DUPLICATE_APPLICATION' ? 'That candidate is already in this pipeline.' : apply.error.message}</Alert></div>}
      <ul className="mt-3 divide-y divide-slate-100">
        {data?.content.slice(0, 6).map((c) => (
          <li key={c.id} className="flex items-center justify-between py-2 text-sm">
            <span><span className="font-medium">{c.fullName}</span> <span className="text-slate-500">· {c.currentTitle ?? '—'} · {c.location ?? '—'}</span></span>
            <Button variant="secondary" loading={apply.isPending && apply.variables?.candidateId === c.id}
              onClick={() => apply.mutate({ candidateId: c.id, jobId }, { onSuccess: () => onAdded(c.fullName) })}>Add</Button>
          </li>
        ))}
      </ul>
    </Card>
  )
}
