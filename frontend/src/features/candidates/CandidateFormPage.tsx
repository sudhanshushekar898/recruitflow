import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { z } from 'zod'
import { Alert, Button, Card, SelectField, Spinner, TextAreaField, TextField } from '../../components/ui'
import { applyApiError } from '../../lib/forms'
import { ApiError } from '../../lib/errors'
import type { CandidateSource, CandidateView, LegalBasis } from '../../types'
import { type CandidateInput, type ConsentInput, type SkillDraft, useCandidate, useCandidateMutations } from './api'
import { LEGAL_BASES, SOURCES } from './labels'
import { SkillsEditor } from './SkillsEditor'

const schema = z.object({
  firstName: z.string().trim().min(1, 'Enter a first name').max(100),
  lastName: z.string().trim().min(1, 'Enter a last name').max(100),
  email: z.string().trim().min(1, 'Enter an email').email('Enter a valid email'),
  phone: z.string().trim().refine((v) => v === '' || /^[+0-9 ()-]{5,30}$/.test(v), 'Use digits, spaces and + ( ) -'),
  currentTitle: z.string().trim().max(150),
  currentCompany: z.string().trim().max(150),
  location: z.string().trim().max(150),
  yearsExperience: z.string().trim().refine((v) => v === '' || (/^\d{1,2}$/.test(v) && Number(v) <= 60), 'Enter a number from 0 to 60'),
  source: z.enum(['LINKEDIN', 'REFERRAL', 'JOB_BOARD', 'AGENCY', 'OTHER']),
  linkedinUrl: z.string().trim().refine((v) => v === '' || /^https:\/\/\S{3,290}$/.test(v), 'Must start with https://'),
  summary: z.string().max(5000, 'Keep the summary under 5000 characters'),
  consentBasis: z.enum(['', 'CONSENT', 'LEGITIMATE_INTEREST']),
  consentGivenAt: z.string(),
  consentExpiresAt: z.string(),
  consentEvidence: z.string().max(300),
}).refine((v) => v.consentBasis === '' || v.consentGivenAt !== '', {
  path: ['consentGivenAt'],
  message: 'Enter the date consent was given',
})
type Values = z.infer<typeof schema>

const EMPTY: Values = {
  firstName: '', lastName: '', email: '', phone: '', currentTitle: '', currentCompany: '', location: '',
  yearsExperience: '', source: 'OTHER', linkedinUrl: '', summary: '',
  consentBasis: '', consentGivenAt: '', consentExpiresAt: '', consentEvidence: '',
}

const fromCandidate = (c: CandidateView): Values => ({
  ...EMPTY,
  firstName: c.firstName,
  lastName: c.lastName,
  email: c.email,
  phone: c.phone ?? '',
  currentTitle: c.currentTitle ?? '',
  currentCompany: c.currentCompany ?? '',
  location: c.location ?? '',
  yearsExperience: c.yearsExperience === undefined ? '' : String(c.yearsExperience),
  source: c.source,
  linkedinUrl: c.linkedinUrl ?? '',
  summary: c.summary ?? '',
})

const toIso = (date: string) => `${date}T00:00:00Z`

/** Create (/candidates/new) and edit (/candidates/:id/edit) share one form. */
export function CandidateFormPage() {
  const { id } = useParams()
  const editing = id !== undefined
  const { data: existing, isPending, isError } = useCandidate(id ?? '')
  const loading = editing && isPending

  if (loading) return <Spinner />
  if (editing && (isError || !existing)) return <Alert>This candidate could not be loaded.</Alert>
  if (existing?.status === 'ARCHIVED') {
    return (
      <Alert tone="info">
        Archived candidates cannot be edited. <Link to={`/candidates/${existing.id}`} className="underline">Open the candidate</Link> to restore them first.
      </Alert>
    )
  }
  // key forces a fresh form (and fresh skills state) once the candidate has loaded
  return <CandidateForm key={existing?.id ?? 'new'} existing={existing} />
}

function CandidateForm({ existing }: { existing?: CandidateView }) {
  const navigate = useNavigate()
  const { create, update } = useCandidateMutations()
  const [formError, setFormError] = useState<string | null>(null)
  const [duplicateId, setDuplicateId] = useState<string | null>(null)
  const [skills, setSkills] = useState<SkillDraft[]>(
    existing?.skills.map((s) => ({ skillId: s.skillId, displayName: s.displayName, years: s.years, level: s.level })) ?? [],
  )
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: existing ? fromCandidate(existing) : EMPTY })
  const consentBasis = useWatch({ control, name: 'consentBasis' })

  const onSubmit = handleSubmit(async (v) => {
    setFormError(null)
    setDuplicateId(null)
    const text = (value: string) => (value === '' ? undefined : value)
    try {
      if (existing) {
        // empty strings clear optional text fields on the server
        const changes: Partial<CandidateInput> = {
          firstName: v.firstName, lastName: v.lastName, email: v.email, phone: v.phone, currentTitle: v.currentTitle,
          currentCompany: v.currentCompany, location: v.location, source: v.source as CandidateSource,
          linkedinUrl: v.linkedinUrl, summary: v.summary,
          yearsExperience: v.yearsExperience === '' ? undefined : Number(v.yearsExperience),
        }
        await update.mutateAsync({ id: existing.id, version: existing.version, changes, skills })
        navigate(`/candidates/${existing.id}`)
      } else {
        const consent: ConsentInput | undefined = v.consentBasis
          ? {
              legalBasis: v.consentBasis as LegalBasis,
              givenAt: toIso(v.consentGivenAt),
              expiresAt: v.consentExpiresAt ? toIso(v.consentExpiresAt) : undefined,
              evidence: text(v.consentEvidence),
              withdrawn: false,
            }
          : undefined
        const created = await create.mutateAsync({
          candidate: {
            firstName: v.firstName, lastName: v.lastName, email: v.email, phone: text(v.phone),
            currentTitle: text(v.currentTitle), currentCompany: text(v.currentCompany), location: text(v.location),
            yearsExperience: v.yearsExperience === '' ? undefined : Number(v.yearsExperience),
            source: v.source as CandidateSource, linkedinUrl: text(v.linkedinUrl), summary: text(v.summary),
          },
          skills,
          consent,
        })
        navigate(`/candidates/${created.id}`)
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DUPLICATE_CANDIDATE') {
        const existingId = error.extensions.existingCandidateId
        setDuplicateId(typeof existingId === 'string' ? existingId : null)
        setError('email', { message: 'A candidate with this email already exists' })
      } else if (error instanceof ApiError && error.code === 'STALE_VERSION') {
        setFormError('Someone else changed this candidate. Reload the page to see their changes, then try again.')
      } else {
        setFormError(applyApiError(error, setError))
      }
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-semibold">{existing ? `Edit ${existing.firstName} ${existing.lastName}` : 'New candidate'}</h1>
      {formError && <Alert>{formError}</Alert>}
      {duplicateId && (
        <Alert>
          This email already belongs to a candidate. <Link to={`/candidates/${duplicateId}`} className="font-medium underline">Open the existing record</Link>.
        </Alert>
      )}

      <Card title="Profile">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="First name" autoComplete="off" error={errors.firstName?.message} {...register('firstName')} />
          <TextField label="Last name" autoComplete="off" error={errors.lastName?.message} {...register('lastName')} />
          <TextField label="Email" type="email" autoComplete="off" error={errors.email?.message} {...register('email')} />
          <TextField label="Phone" type="tel" autoComplete="off" error={errors.phone?.message} {...register('phone')} />
          <TextField label="Current title" error={errors.currentTitle?.message} {...register('currentTitle')} />
          <TextField label="Current company" error={errors.currentCompany?.message} {...register('currentCompany')} />
          <TextField label="Location" error={errors.location?.message} {...register('location')} />
          <TextField label="Years of experience" inputMode="numeric" error={errors.yearsExperience?.message} {...register('yearsExperience')} />
          <SelectField label="Source" {...register('source')}>
            {Object.entries(SOURCES).map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </SelectField>
          <TextField label="LinkedIn URL" error={errors.linkedinUrl?.message} {...register('linkedinUrl')} />
        </div>
        <TextAreaField label="Summary" className="mt-4" error={errors.summary?.message} {...register('summary')} />
      </Card>

      <Card title="Skills">
        <SkillsEditor value={skills} onChange={setSkills} />
      </Card>

      {!existing && (
        <Card title="Consent and retention (optional)">
          <p className="mb-4 text-sm text-slate-600">
            Record why you hold this person&apos;s data. Without it, data is kept for the default retention period of 24 months.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField label="Legal basis" {...register('consentBasis')}>
              <option value="">Not recorded yet</option>
              {Object.entries(LEGAL_BASES).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </SelectField>
            {consentBasis && (
              <>
                <TextField label="Date given" type="date" error={errors.consentGivenAt?.message} {...register('consentGivenAt')} />
                <TextField label="Expires (optional)" type="date" {...register('consentExpiresAt')} />
                <TextField label="Evidence (optional)" hint="For example: email thread, signed form" {...register('consentEvidence')} />
              </>
            )}
          </div>
        </Card>
      )}

      <div className="flex gap-3">
        <Button type="submit" loading={isSubmitting}>{existing ? 'Save changes' : 'Create candidate'}</Button>
        <Link
          to={existing ? `/candidates/${existing.id}` : '/candidates'}
          className="inline-flex items-center rounded-md px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50"
        >
          Cancel
        </Link>
      </div>
    </form>
  )
}
