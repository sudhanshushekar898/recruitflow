import type { CandidateSource, CandidateStatus, LegalBasis, SkillLevel } from '../../types'

export const SOURCES: Record<CandidateSource, string> = {
  LINKEDIN: 'LinkedIn',
  REFERRAL: 'Referral',
  JOB_BOARD: 'Job board',
  AGENCY: 'Agency',
  OTHER: 'Other',
}

export const STATUSES: Record<CandidateStatus, string> = {
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
  DO_NOT_CONTACT: 'Do not contact',
  ANONYMISED: 'Anonymised',
}

export const LEVELS: Record<SkillLevel, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
  EXPERT: 'Expert',
}

export const LEGAL_BASES: Record<LegalBasis, string> = {
  CONSENT: 'Consent',
  LEGITIMATE_INTEREST: 'Legitimate interest',
}

const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
const dateTimeFormat = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'UTC',
})

export const formatDate = (iso: string) => dateFormat.format(new Date(iso))
export const formatDateTime = (iso: string) => `${dateTimeFormat.format(new Date(iso))} UTC`
