import { Clock, MapPin, Video } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Avatar, Badge } from '../../components/ui'
import type { InterviewView } from '../../types'
import { INTERVIEW_STATUS, INTERVIEW_TONE, TYPES, formatTime, isLink, relativeDay } from './labels'

/** One interview as a row: when, who, for which job, and whether feedback is still owed. */
export function InterviewCard({ interview: i }: { interview: InterviewView }) {
  const feedbackDue = i.canGiveFeedback && new Date(i.scheduledStart) < new Date()
  return (
    <Link
      to={`/interviews/${i.id}`}
      className="flex items-center gap-4 px-5 py-4 transition hover:bg-slate-50"
    >
      <div className="w-20 shrink-0 text-center">
        <p className="text-xs font-semibold uppercase tracking-wide text-indigo-600">{relativeDay(i.scheduledStart)}</p>
        <p className="text-lg font-bold tabular-nums text-slate-900">{formatTime(i.scheduledStart)}</p>
      </div>
      <div className="h-10 w-px bg-slate-200" aria-hidden />
      <Avatar name={i.candidateName} />
      <div className="min-w-0 flex-1">
        <p className="truncate font-semibold text-slate-900">{i.candidateName}</p>
        <p className="truncate text-sm text-slate-500">{i.jobTitle}{i.clientName ? ` · ${i.clientName}` : ''} · Round {i.roundNo}</p>
        <p className="mt-0.5 flex items-center gap-3 text-xs text-slate-500">
          <span className="inline-flex items-center gap-1"><Clock className="h-3 w-3" aria-hidden />{TYPES[i.type]}</span>
          {i.locationOrLink && (
            <span className="inline-flex items-center gap-1 truncate">
              {isLink(i.locationOrLink) ? <Video className="h-3 w-3" aria-hidden /> : <MapPin className="h-3 w-3" aria-hidden />}
              {isLink(i.locationOrLink) ? 'Online' : i.locationOrLink}
            </span>
          )}
        </p>
      </div>
      <div className="hidden -space-x-2 sm:flex" aria-label="Panel">
        {i.participants.map((p) => (
          <span key={p.userId} className="rounded-full ring-2 ring-white" title={p.name}><Avatar name={p.name} size="sm" /></span>
        ))}
      </div>
      <div className="flex w-28 flex-col items-end gap-1">
        <Badge tone={INTERVIEW_TONE[i.status]}>{INTERVIEW_STATUS[i.status]}</Badge>
        {feedbackDue && <Badge tone="amber">Feedback due</Badge>}
      </div>
    </Link>
  )
}
