import { ArrowLeft, CheckCircle2, Clock, EyeOff, MapPin, Star, Video, XCircle } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Alert, Avatar, Badge, Button, Card, EmptyState, Spinner, TextAreaField } from '../../components/ui'
import { isApiError } from '../../lib/apiClient'
import type { FeedbackView, InterviewView, Recommendation } from '../../types'
import { STAGE_LABEL } from '../jobs/labels'
import { useFeedback, useInterview, useInterviewMutations } from './api'
import { INTERVIEW_STATUS, INTERVIEW_TONE, RECOMMENDATIONS, RECOMMENDATION_TONE, TYPES, formatRange, isLink } from './labels'

export function InterviewDetailPage() {
  const { id = '' } = useParams()
  const { data: interview, isPending, isError, error } = useInterview(id)

  if (isPending) return <Spinner />
  if (isError || !interview) {
    return <Alert>{isApiError(error, 'FORBIDDEN') ? 'You are not on this interview’s panel.' : 'This interview could not be loaded.'}</Alert>
  }
  return (
    <div className="space-y-6">
      <Link to={interview.canManage ? '/interviews' : '/my-interviews'} className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-indigo-600">
        <ArrowLeft className="h-4 w-4" aria-hidden /> Interviews
      </Link>
      <Header interview={interview} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          {interview.canGiveFeedback && <FeedbackForm interviewId={interview.id} />}
          <FeedbackSection interviewId={interview.id} />
        </div>
        <Panel interview={interview} />
      </div>
    </div>
  )
}

function Header({ interview: i }: { interview: InterviewView }) {
  const { finish } = useInterviewMutations()
  return (
    <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200/70">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-center gap-4">
          <Avatar name={i.candidateName} size="lg" />
          <div>
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl font-bold tracking-tight">
                {i.candidateId ? <Link to={`/candidates/${i.candidateId}`} className="hover:text-indigo-600">{i.candidateName}</Link> : i.candidateName}
              </h1>
              <Badge tone={INTERVIEW_TONE[i.status]}>{INTERVIEW_STATUS[i.status]}</Badge>
            </div>
            <p className="text-sm text-slate-500">
              {i.candidateTitle ? `${i.candidateTitle} · ` : ''}
              {i.jobId ? <Link to={`/jobs/${i.jobId}/pipeline`} className="hover:text-indigo-600">{i.jobTitle}</Link> : i.jobTitle}
              {i.clientName ? ` · ${i.clientName}` : ''}
            </p>
          </div>
        </div>
        {i.canManage && i.status === 'SCHEDULED' && (
          <div className="flex gap-2">
            <Button variant="secondary" icon={CheckCircle2} loading={finish.isPending && finish.variables?.action === 'complete'}
              onClick={() => finish.mutate({ id: i.id, action: 'complete' })}>Mark completed</Button>
            <Button variant="ghost" icon={XCircle} loading={finish.isPending && finish.variables?.action === 'cancel'}
              onClick={() => finish.mutate({ id: i.id, action: 'cancel' })}>Cancel interview</Button>
          </div>
        )}
      </div>
      <dl className="mt-6 grid gap-4 border-t border-slate-100 pt-5 sm:grid-cols-4">
        <Fact label="When" icon={Clock}>{formatRange(i.scheduledStart, i.scheduledEnd)}</Fact>
        <Fact label="Type" icon={Star}>{TYPES[i.type]} · Round {i.roundNo}</Fact>
        <Fact label="Where" icon={isLink(i.locationOrLink) ? Video : MapPin}>
          {isLink(i.locationOrLink)
            ? <a href={i.locationOrLink} target="_blank" rel="noreferrer noopener" className="text-indigo-600 hover:underline">Join the call</a>
            : i.locationOrLink ?? '—'}
        </Fact>
        <Fact label="Pipeline stage" icon={CheckCircle2}>{i.applicationStatus ? STAGE_LABEL[i.applicationStatus as keyof typeof STAGE_LABEL] : '—'}</Fact>
      </dl>
      {finish.isError && <div className="mt-4"><Alert>{finish.error.message}</Alert></div>}
    </div>
  )
}

function Fact({ label, icon: Icon, children }: { label: string; icon: typeof Clock; children: React.ReactNode }) {
  return (
    <div>
      <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-500"><Icon className="h-3.5 w-3.5" aria-hidden />{label}</dt>
      <dd className="mt-1 text-sm font-medium text-slate-900">{children}</dd>
    </div>
  )
}

function Panel({ interview }: { interview: InterviewView }) {
  return (
    <Card title="Panel">
      <ul className="space-y-3">
        {interview.participants.map((p) => (
          <li key={p.userId} className="flex items-center gap-3">
            <Avatar name={p.name} size="sm" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{p.name}</p>
              <p className="text-xs text-slate-500">{p.role === 'LEAD' ? 'Lead interviewer' : 'Panel'}</p>
            </div>
            {p.feedbackSubmitted
              ? <Badge tone="green">Feedback in</Badge>
              : <Badge tone={interview.status === 'CANCELLED' ? 'neutral' : 'amber'}>Pending</Badge>}
          </li>
        ))}
      </ul>
    </Card>
  )
}

const REC_ORDER: Recommendation[] = ['STRONG_NO', 'NO', 'YES', 'STRONG_YES']

function FeedbackForm({ interviewId }: { interviewId: string }) {
  const { submitFeedback } = useInterviewMutations()
  const [rating, setRating] = useState(0)
  const [recommendation, setRecommendation] = useState<Recommendation | null>(null)
  const [strengths, setStrengths] = useState('')
  const [concerns, setConcerns] = useState('')
  const ready = rating > 0 && recommendation !== null

  return (
    <Card title="Your feedback" description="Colleagues' feedback stays hidden until you submit yours, so nobody anchors on anyone else.">
      <form
        className="space-y-5"
        onSubmit={(e) => {
          e.preventDefault()
          if (ready) submitFeedback.mutate({ id: interviewId, rating, recommendation: recommendation!, strengths: strengths || undefined, concerns: concerns || undefined })
        }}
      >
        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Overall rating</legend>
          <div className="mt-2 flex gap-1">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} type="button" aria-label={`${n} out of 5`} aria-pressed={rating === n} onClick={() => setRating(n)}
                className="rounded-md p-1 transition hover:scale-110">
                <Star className={`h-7 w-7 ${n <= rating ? 'fill-amber-400 text-amber-400' : 'text-slate-300'}`} aria-hidden />
              </button>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Recommendation</legend>
          <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {REC_ORDER.map((r) => (
              <button key={r} type="button" aria-pressed={recommendation === r} onClick={() => setRecommendation(r)}
                className={`rounded-lg px-3 py-2 text-sm font-semibold ring-1 ring-inset transition ${
                  recommendation === r
                    ? r.endsWith('YES') ? 'bg-emerald-600 text-white ring-emerald-600' : 'bg-rose-600 text-white ring-rose-600'
                    : 'bg-white text-slate-700 ring-slate-300 hover:bg-slate-50'}`}>
                {RECOMMENDATIONS[r]}
              </button>
            ))}
          </div>
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextAreaField label="Strengths" value={strengths} onChange={(e) => setStrengths(e.target.value)} maxLength={4000} />
          <TextAreaField label="Concerns" value={concerns} onChange={(e) => setConcerns(e.target.value)} maxLength={4000} />
        </div>
        {submitFeedback.isError && <Alert>{submitFeedback.error.message}</Alert>}
        <Button type="submit" disabled={!ready} loading={submitFeedback.isPending}>Submit feedback</Button>
      </form>
    </Card>
  )
}

function FeedbackSection({ interviewId }: { interviewId: string }) {
  const { data, isPending } = useFeedback(interviewId)
  return (
    <Card title="Feedback">
      {isPending && <Spinner />}
      {data?.blinded && (
        <EmptyState icon={EyeOff} title="Feedback is hidden until you give yours">
          {data.hiddenCount > 0 ? `${data.hiddenCount} colleague${data.hiddenCount === 1 ? ' has' : 's have'} already submitted.` : 'Nobody has submitted yet.'}
        </EmptyState>
      )}
      {data && !data.blinded && data.feedback.length === 0 && <EmptyState icon={Star} title="No feedback yet" />}
      <ul className="space-y-4">
        {data?.feedback.map((f) => <FeedbackItem key={f.id} feedback={f} />)}
      </ul>
    </Card>
  )
}

function FeedbackItem({ feedback: f }: { feedback: FeedbackView }) {
  return (
    <li className="rounded-lg border border-slate-200 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <Avatar name={f.interviewerName} size="sm" />
          <span className="text-sm font-semibold">{f.interviewerName}{f.mine ? ' (you)' : ''}</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="flex" aria-label={`${f.rating} out of 5`}>
            {[1, 2, 3, 4, 5].map((n) => <Star key={n} className={`h-4 w-4 ${n <= f.rating ? 'fill-amber-400 text-amber-400' : 'text-slate-200'}`} aria-hidden />)}
          </span>
          <Badge tone={RECOMMENDATION_TONE[f.recommendation]}>{RECOMMENDATIONS[f.recommendation]}</Badge>
        </div>
      </div>
      {(f.strengths || f.concerns) && (
        <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
          {f.strengths && <div><dt className="text-xs font-semibold uppercase text-emerald-700">Strengths</dt><dd className="mt-0.5 text-slate-700">{f.strengths}</dd></div>}
          {f.concerns && <div><dt className="text-xs font-semibold uppercase text-rose-700">Concerns</dt><dd className="mt-0.5 text-slate-700">{f.concerns}</dd></div>}
        </dl>
      )}
    </li>
  )
}
