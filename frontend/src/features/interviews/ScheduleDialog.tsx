import { CalendarPlus } from 'lucide-react'
import { useState } from 'react'
import { Alert, Avatar, Button, SelectField, TextField } from '../../components/ui'
import { ApiError } from '../../lib/errors'
import type { ApplicationView, InterviewType } from '../../types'
import { useInterviewMutations, useInterviewers } from './api'
import { TYPES } from './labels'

function tomorrowDate() {
  const d = new Date(Date.now() + 86_400_000)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Schedules an interview for one application; clashes come back from the server with who is busy. */
export function ScheduleDialog({ application, onClose, onScheduled }: {
  application: ApplicationView
  onClose: () => void
  onScheduled: (message: string) => void
}) {
  const { data: interviewers = [] } = useInterviewers()
  const { schedule } = useInterviewMutations()
  const [type, setType] = useState<InterviewType>('VIDEO')
  const [date, setDate] = useState(tomorrowDate())
  const [time, setTime] = useState('10:00')
  const [minutes, setMinutes] = useState(60)
  const [where, setWhere] = useState('')
  const [panel, setPanel] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)

  function toggle(id: string) {
    setPanel(panel.includes(id) ? panel.filter((p) => p !== id) : [...panel, id])
  }

  function submit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (panel.length === 0) {
      setError('Choose at least one interviewer.')
      return
    }
    const start = new Date(`${date}T${time}`)
    const end = new Date(start.getTime() + minutes * 60_000)
    schedule.mutate(
      {
        applicationId: application.id,
        type,
        start: start.toISOString(),
        end: end.toISOString(),
        locationOrLink: where.trim() || undefined,
        participants: panel.map((userId, i) => ({ userId, role: i === 0 ? 'LEAD' : 'PANEL' })),
      },
      {
        onSuccess: () => onScheduled(`Interview scheduled with ${application.candidateName}.`),
        onError: (err) => {
          if (err instanceof ApiError && err.code === 'INTERVIEW_CONFLICT') {
            const names = [...new Set(((err.extensions.conflicts as { userName?: string }[]) ?? []).map((c) => c.userName).filter(Boolean))]
            setError(`${names.join(' and ') || 'An interviewer'} already ${names.length > 1 ? 'have' : 'has'} an interview at that time. Pick another slot.`)
          } else {
            setError(err.message)
          }
        },
      },
    )
  }

  return (
    <div role="dialog" aria-modal="true" aria-label={`Schedule an interview with ${application.candidateName}`}
      className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm">
      <form onSubmit={submit} className="w-full max-w-lg space-y-5 rounded-2xl bg-white p-6 shadow-2xl">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600"><CalendarPlus className="h-5 w-5" aria-hidden /></span>
          <div>
            <h2 className="text-lg font-semibold">Schedule an interview</h2>
            <p className="text-sm text-slate-500">{application.candidateName} · {application.jobTitle}</p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Type" value={type} onChange={(e) => setType(e.target.value as InterviewType)}>
            {Object.entries(TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </SelectField>
          <SelectField label="Duration" value={minutes} onChange={(e) => setMinutes(Number(e.target.value))}>
            {[30, 45, 60, 90].map((m) => <option key={m} value={m}>{m} minutes</option>)}
          </SelectField>
          <TextField label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          <TextField label="Start time" type="time" value={time} onChange={(e) => setTime(e.target.value)} />
        </div>
        <TextField label="Video link or location" value={where} onChange={(e) => setWhere(e.target.value)} placeholder="https://meet… or Office, room 2" />

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Interviewers <span className="font-normal text-slate-500">(the first one you pick leads)</span></legend>
          <ul className="mt-2 space-y-1.5">
            {interviewers.map((person) => (
              <li key={person.id}>
                <label className={`flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2 ring-1 ring-inset transition ${panel.includes(person.id) ? 'bg-indigo-50 ring-indigo-300' : 'ring-slate-200 hover:bg-slate-50'}`}>
                  <input type="checkbox" checked={panel.includes(person.id)} onChange={() => toggle(person.id)} className="h-4 w-4 rounded text-indigo-600" />
                  <Avatar name={person.name} size="sm" />
                  <span className="flex-1 text-sm font-medium">{person.name}</span>
                  {panel[0] === person.id && <span className="text-xs font-semibold text-indigo-600">Lead</span>}
                </label>
              </li>
            ))}
            {interviewers.length === 0 && <li className="text-sm text-slate-500">No interviewers yet. An admin can add users with the Interviewer role.</li>}
          </ul>
        </fieldset>

        {error && <Alert>{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={schedule.isPending}>Schedule</Button>
        </div>
      </form>
    </div>
  )
}
