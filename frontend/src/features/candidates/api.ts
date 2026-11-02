import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/apiClient'
import type { CandidateSource, CandidateStatus, CandidateSummary, CandidateView, LegalBasis, NoteView, PageResponse, SkillLevel } from '../../types'

export interface CandidateFilters {
  q: string
  location: string
  minYears: string
  source: CandidateSource | ''
  status: CandidateStatus | ''
  skillIds: string[]
  page: number
}

export const PAGE_SIZE = 10

export function useCandidates(filters: CandidateFilters) {
  return useQuery({
    queryKey: ['candidates', 'list', filters],
    queryFn: ({ signal }) =>
      api<PageResponse<CandidateSummary>>('/api/v1/candidates', {
        query: {
          q: filters.q,
          location: filters.location,
          minYears: filters.minYears,
          source: filters.source,
          status: filters.status,
          skillId: filters.skillIds,
          page: filters.page,
          size: PAGE_SIZE,
          sort: 'lastName,asc',
        },
        signal,
      }),
    placeholderData: keepPreviousData,
  })
}

export function useCandidate(id: string) {
  return useQuery({
    queryKey: ['candidates', 'detail', id],
    queryFn: ({ signal }) => api<CandidateView>(`/api/v1/candidates/${id}`, { signal }),
    enabled: id !== '', // the create form has no id and must not fetch anything
  })
}

export function useNotes(id: string) {
  return useQuery({
    queryKey: ['candidates', 'notes', id],
    queryFn: ({ signal }) => api<NoteView[]>(`/api/v1/candidates/${id}/notes`, { signal }),
  })
}

export interface SkillDraft {
  skillId: string
  displayName: string
  years?: number
  level?: SkillLevel
}

export interface CandidateInput {
  firstName: string
  lastName: string
  email: string
  phone?: string
  currentTitle?: string
  currentCompany?: string
  location?: string
  yearsExperience?: number
  source: CandidateSource
  summary?: string
  linkedinUrl?: string
}

export interface ConsentInput {
  legalBasis: LegalBasis
  givenAt: string
  expiresAt?: string
  evidence?: string
  withdrawn: boolean
}

const skillsBody = (skills: SkillDraft[]) => skills.map(({ skillId, years, level }) => ({ skillId, years, level }))

/** Every mutation refreshes whatever candidate data is on screen. */
export function useCandidateMutations() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['candidates'] })

  return {
    create: useMutation({
      mutationFn: (input: { candidate: CandidateInput; skills: SkillDraft[]; consent?: ConsentInput }) =>
        api<CandidateView>('/api/v1/candidates', {
          method: 'POST',
          body: { ...input.candidate, skills: skillsBody(input.skills), consent: input.consent },
        }),
      onSuccess: refresh,
    }),
    update: useMutation({
      mutationFn: async (input: { id: string; version: number; changes: Partial<CandidateInput>; skills?: SkillDraft[] }) => {
        const updated = await api<CandidateView>(`/api/v1/candidates/${input.id}`, {
          method: 'PATCH',
          body: { ...input.changes, version: input.version },
        })
        if (!input.skills) return updated
        return api<CandidateView>(`/api/v1/candidates/${input.id}/skills`, {
          method: 'PUT',
          body: { skills: skillsBody(input.skills) },
        })
      },
      onSuccess: refresh,
    }),
    setArchived: useMutation({
      mutationFn: (input: { id: string; archived: boolean }) =>
        api<CandidateView>(`/api/v1/candidates/${input.id}/${input.archived ? 'archive' : 'restore'}`, { method: 'POST' }),
      onSuccess: refresh,
    }),
    addNote: useMutation({
      mutationFn: (input: { id: string; body: string }) =>
        api<NoteView>(`/api/v1/candidates/${input.id}/notes`, { method: 'POST', body: { body: input.body } }),
      onSuccess: refresh,
    }),
    recordConsent: useMutation({
      mutationFn: (input: { id: string; consent: ConsentInput }) =>
        api<CandidateView>(`/api/v1/candidates/${input.id}/consent`, { method: 'PUT', body: input.consent }),
      onSuccess: refresh,
    }),
  }
}
