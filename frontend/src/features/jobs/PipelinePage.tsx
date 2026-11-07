import { ArrowLeft, Clock, GripVertical, UserPlus, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert, Avatar, Badge, Button, Card, SelectField, Spinner, TextAreaField, TextField } from '../../components/ui'
import { ApiError } from '../../lib/errors'
import type { ApplicationStatus, ApplicationView, RejectionReason } from '../../types'
import { useCandidates } from '../candidates/api'
import { useJobMutations, usePipeline } from './api'
import { JOB_STATUSES, JOB_TONE, REASONS, STAGE_ACCENT, STAGE_LABEL, daysSince } from './labels'

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
  const filled = Math.min(100, Math.round((board.hired / Math.max(1, board.openings)) * 100))

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
    <div className="space-y-5">
      <Link to={`/jobs/${board.jobId}`} className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-indigo-600">
        <ArrowLeft className="h-4 w-4" aria-hidden /> {board.jobTitle}
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-4 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200/70">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight">{board.jobTitle} pipeline</h1>
            <Badge tone={JOB_TONE[board.jobStatus]}>{JOB_STATUSES[board.jobStatus]}</Badge>
          </div>
          <p className="mt-1 text-sm text-slate-500">{board.clientName} · {all.length} candidates · drag a card to move it</p>
        </div>
        <div className="flex items-center gap-5">
          <div className="w-48">
            <div className="mb-1 flex justify-between text-xs font-medium text-slate-500">
              <span>Openings filled</span><span className="tabular-nums">{board.hired} / {board.openings}</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-emerald-500 transition-all" style={{ width: `${filled}%` }} />
            </div>
          </div>
          {board.jobStatus === 'OPEN' && <AddCandidate jobId={board.jobId} onAdded={(name) => setMessage({ tone: 'success', text: `${name} added to the pipeline.` })} />}
        </div>
      </div>

      {message && <Alert tone={message.tone}>{message.text}</Alert>}

      <div className="-mx-4 flex gap-4 overflow-x-auto px-4 pb-4 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8" aria-label="Pipeline board">
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
            className={`flex w-72 shrink-0 flex-col rounded-xl border-t-4 ${STAGE_ACCENT[stage.status]} p-3 transition ${
              dragOver === stage.status ? 'bg-indigo-50 ring-2 ring-indigo-300' : 'bg-slate-100/80'
            }`}
          >
            <h2 className="mb-3 flex items-center justify-between px-1 text-sm font-semibold text-slate-700">
              {STAGE_LABEL[stage.status]}
              <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold tabular-nums text-slate-600 shadow-sm">{stage.count}</span>
            </h2>
            <ul className="min-h-24 space-y-2.5">
              {stage.applications.map((a) => (
                <li
                  key={a.id}
                  draggable
                  onDragStart={(e) => e.dataTransfer.setData('text/plain', a.id)}
                  className="group cursor-grab rounded-lg bg-white p-3 shadow-sm ring-1 ring-slate-200 transition hover:shadow-md hover:ring-indigo-200 active:cursor-grabbing"
                >
                  <div className="flex items-start gap-2.5">
                    <Avatar name={a.candidateName} size="sm" />
                    <div className="min-w-0 flex-1">
                      <Link to={`/candidates/${a.candidateId}`} className="block truncate text-sm font-semibold text-slate-900 hover:text-indigo-600">{a.candidateName}</Link>
                      <p className="truncate text-xs text-slate-500">{a.candidateTitle ?? '—'}</p>
                    </div>
                    <GripVertical className="h-4 w-4 shrink-0 text-slate-300 group-hover:text-slate-400" aria-hidden />
                  </div>
                  <div className="mt-2.5 flex items-center justify-between text-xs text-slate-500">
                    <span className="truncate">{a.ownerName}</span>
                    <span className={`inline-flex items-center gap-1 ${daysSince(a.statusChangedAt) > 14 && !['HIRED', 'REJECTED', 'WITHDRAWN'].includes(a.status) ? 'font-medium text-amber-600' : ''}`}>
                      <Clock className="h-3 w-3" aria-hidden />{daysSince(a.statusChangedAt)}d
                    </span>
                  </div>
                  {a.rejectionReason && <p className="mt-2 rounded bg-rose-50 px-2 py-1 text-xs text-rose-700">{REASONS[a.rejectionReason]}</p>}
                  {a.allowedNext.length > 0 && (
                    <select
                      aria-label={`Move ${a.candidateName}`}
                      value=""
                      onChange={(e) => e.target.value && request(a, e.target.value as ApplicationStatus)}
                      className="mt-2.5 w-full rounded-md border-0 bg-slate-50 px-2 py-1 text-xs text-slate-600 ring-1 ring-inset ring-slate-200 focus:ring-2 focus:ring-indigo-500"
                    >
                      <option value="">Move to…</option>
                      {a.allowedNext.map((s) => <option key={s} value={s}>{STAGE_LABEL[s]}</option>)}
                    </select>
                  )}
                </li>
              ))}
              {stage.applications.length === 0 && (
                <li className="rounded-lg border-2 border-dashed border-slate-200 px-3 py-6 text-center text-xs text-slate-400">Drop here</li>
              )}
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
    <div role="dialog" aria-modal="true" aria-label={`Reject ${application.candidateName}`} className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
      <form
        className="w-full max-w-md space-y-4 rounded-2xl bg-white p-6 shadow-2xl"
        onSubmit={(e) => { e.preventDefault(); if (reason) onConfirm(reason, comment) }}
      >
        <div className="flex items-center gap-3">
          <Avatar name={application.candidateName} />
          <div>
            <h2 className="text-lg font-semibold">Reject {application.candidateName}</h2>
            <p className="text-sm text-slate-500">The reason is kept in the application history.</p>
          </div>
        </div>
        <SelectField label="Reason" value={reason} onChange={(e) => setReason(e.target.value as RejectionReason)}>
          <option value="">Choose a reason…</option>
          {Object.entries(REASONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </SelectField>
        <TextAreaField label="Comment (optional)" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onCancel}>Cancel</Button>
          <Button type="submit" variant="danger" loading={busy} disabled={!reason}>Reject</Button>
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
  if (!open) return <Button icon={UserPlus} onClick={() => setOpen(true)}>Add candidate</Button>
  return (
    <div role="dialog" aria-modal="true" aria-label="Add a candidate to this job" className="fixed inset-0 z-40 flex items-start justify-center bg-slate-900/50 p-4 pt-24 backdrop-blur-sm">
      <div className="w-full max-w-lg">
        <Card title="Add a candidate to this job" action={<button type="button" aria-label="Close" onClick={() => setOpen(false)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"><X className="h-5 w-5" /></button>}>
          <form className="flex items-end gap-2" onSubmit={(e) => { e.preventDefault(); setApplied(q.trim()) }}>
            <TextField label="Search candidates" className="flex-1" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Name, title, location" autoFocus />
            <Button type="submit">Search</Button>
          </form>
          {apply.isError && <div className="mt-3"><Alert>{apply.error instanceof ApiError && apply.error.code === 'DUPLICATE_APPLICATION' ? 'That candidate is already in this pipeline.' : apply.error.message}</Alert></div>}
          <ul className="mt-4 divide-y divide-slate-100">
            {data?.content.slice(0, 6).map((c) => (
              <li key={c.id} className="flex items-center gap-3 py-2.5 text-sm">
                <Avatar name={c.fullName} size="sm" />
                <span className="min-w-0 flex-1">
                  <span className="block font-medium text-slate-900">{c.fullName}</span>
                  <span className="block truncate text-xs text-slate-500">{c.currentTitle ?? '—'} · {c.location ?? '—'}</span>
                </span>
                <Button variant="secondary" loading={apply.isPending && apply.variables?.candidateId === c.id}
                  onClick={() => apply.mutate({ candidateId: c.id, jobId }, { onSuccess: () => onAdded(c.fullName) })}>Add</Button>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  )
}
