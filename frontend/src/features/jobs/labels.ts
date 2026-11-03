import type { ApplicationStatus, EmploymentType, JobStatus, RejectionReason, SkillImportance } from '../../types'

export const JOB_STATUSES: Record<JobStatus, string> = { DRAFT: 'Draft', OPEN: 'Open', ON_HOLD: 'On hold', CLOSED: 'Closed' }
export const JOB_TONE: Record<JobStatus, 'neutral' | 'green' | 'amber' | 'red'> = { DRAFT: 'neutral', OPEN: 'green', ON_HOLD: 'amber', CLOSED: 'red' }
export const JOB_ACTIONS: Record<JobStatus, string> = { DRAFT: 'Back to draft', OPEN: 'Open job', ON_HOLD: 'Put on hold', CLOSED: 'Close job' }

export const EMPLOYMENT: Record<EmploymentType, string> = { FULL_TIME: 'Full time', PART_TIME: 'Part time', CONTRACT: 'Contract' }
export const IMPORTANCE: Record<SkillImportance, string> = { REQUIRED: 'Required', NICE_TO_HAVE: 'Nice to have' }

export const STAGE_LABEL: Record<ApplicationStatus, string> = {
  NEW: 'New',
  SCREENING: 'Screening',
  SHORTLISTED: 'Shortlisted',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  HIRED: 'Hired',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}
export const STAGE_TONE: Record<ApplicationStatus, 'neutral' | 'green' | 'amber' | 'red'> = {
  NEW: 'neutral', SCREENING: 'neutral', SHORTLISTED: 'neutral', INTERVIEW: 'amber', OFFER: 'amber', HIRED: 'green', REJECTED: 'red', WITHDRAWN: 'neutral',
}

export const REASONS: Record<RejectionReason, string> = {
  SKILLS_MISMATCH: 'Skills mismatch',
  EXPERIENCE: 'Not enough experience',
  SALARY_EXPECTATIONS: 'Salary expectations',
  CULTURE_FIT: 'Culture fit',
  POSITION_FILLED: 'Position filled',
  CANDIDATE_UNRESPONSIVE: 'Candidate unresponsive',
  OTHER: 'Other',
}

export function daysSince(iso: string): number {
  return Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000))
}

export function money(min?: number, max?: number, currency?: string): string {
  if (min === undefined && max === undefined) return '—'
  const fmt = (n: number) => new Intl.NumberFormat('en-GB', { maximumFractionDigits: 0 }).format(n)
  const range = min !== undefined && max !== undefined ? `${fmt(min)} – ${fmt(max)}` : fmt((min ?? max) as number)
  return `${currency ?? ''} ${range}`.trim()
}
