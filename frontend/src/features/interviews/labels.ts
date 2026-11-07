import type { Tone } from '../../components/ui'
import type { InterviewStatus, InterviewType, Recommendation } from '../../types'

export const TYPES: Record<InterviewType, string> = { PHONE: 'Phone screen', VIDEO: 'Video call', ONSITE: 'On-site', TECHNICAL: 'Technical' }

export const INTERVIEW_STATUS: Record<InterviewStatus, string> = { SCHEDULED: 'Scheduled', COMPLETED: 'Completed', CANCELLED: 'Cancelled', NO_SHOW: 'No show' }
export const INTERVIEW_TONE: Record<InterviewStatus, Tone> = { SCHEDULED: 'blue', COMPLETED: 'green', CANCELLED: 'neutral', NO_SHOW: 'red' }

export const RECOMMENDATIONS: Record<Recommendation, string> = { STRONG_YES: 'Strong yes', YES: 'Yes', NO: 'No', STRONG_NO: 'Strong no' }
export const RECOMMENDATION_TONE: Record<Recommendation, Tone> = { STRONG_YES: 'green', YES: 'green', NO: 'red', STRONG_NO: 'red' }

const day = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })
const time = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' })

export const formatDay = (iso: string) => day.format(new Date(iso))
export const formatTime = (iso: string) => time.format(new Date(iso))
export const formatRange = (start: string, end: string) => `${formatDay(start)} · ${formatTime(start)}–${formatTime(end)}`

export function relativeDay(iso: string): string {
  const d = new Date(iso)
  const today = new Date()
  const diff = Math.round((new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime() -
    new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()) / 86_400_000)
  if (diff === 0) return 'Today'
  if (diff === 1) return 'Tomorrow'
  if (diff === -1) return 'Yesterday'
  return formatDay(iso)
}

export const isLink = (value?: string) => !!value && /^https:\/\//.test(value)
