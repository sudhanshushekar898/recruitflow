import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/apiClient'
import type { ApplicationStatus, ApplicationView, ClientView, EmploymentType, JobStatus, JobSummary, JobView, PageResponse, PipelineView, RejectionReason, SkillImportance, StatusChangeResult } from '../../types'

export interface JobFilters {
  q: string
  status: JobStatus | ''
  clientId: string
  page: number
}

export const JOB_PAGE_SIZE = 10

export function useJobs(filters: JobFilters) {
  return useQuery({
    queryKey: ['jobs', 'list', filters],
    queryFn: ({ signal }) =>
      api<PageResponse<JobSummary>>('/api/v1/jobs', {
        query: { q: filters.q, status: filters.status, clientId: filters.clientId, page: filters.page, size: JOB_PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
  })
}

export function useOpenJobs() {
  return useQuery({
    queryKey: ['jobs', 'open'],
    queryFn: ({ signal }) => api<PageResponse<JobSummary>>('/api/v1/jobs', { query: { status: 'OPEN', size: 100, sort: 'title,asc' }, signal }),
  })
}

export function useJob(id: string) {
  return useQuery({
    queryKey: ['jobs', 'detail', id],
    queryFn: ({ signal }) => api<JobView>(`/api/v1/jobs/${id}`, { signal }),
    enabled: id !== '',
  })
}

export function useClients() {
  return useQuery({
    queryKey: ['clients'],
    queryFn: ({ signal }) => api<PageResponse<ClientView>>('/api/v1/clients', { query: { size: 100, sort: 'name,asc' }, signal }),
  })
}

export function usePipeline(jobId: string) {
  return useQuery({
    queryKey: ['pipeline', jobId],
    queryFn: ({ signal }) => api<PipelineView>(`/api/v1/jobs/${jobId}/pipeline`, { signal }),
    enabled: jobId !== '',
  })
}

export function useCandidateApplications(candidateId: string) {
  return useQuery({
    queryKey: ['pipeline', 'candidate', candidateId],
    queryFn: ({ signal }) => api<ApplicationView[]>(`/api/v1/candidates/${candidateId}/applications`, { signal }),
  })
}

export function useRecentApplications() {
  return useQuery({
    queryKey: ['pipeline', 'recent'],
    queryFn: ({ signal }) => api<PageResponse<ApplicationView>>('/api/v1/applications', { query: { size: 8, sort: 'statusChangedAt,desc' }, signal }),
  })
}

export interface SkillRequirement {
  skillId: string
  displayName: string
  importance: SkillImportance
  minYears?: number
  weight: number
}

export interface JobInput {
  clientId: string
  title: string
  description?: string
  location?: string
  employmentType: EmploymentType
  salaryMin?: number
  salaryMax?: number
  currency?: string
  openings?: number
}

const requirements = (skills: SkillRequirement[]) =>
  skills.map(({ skillId, importance, minYears, weight }) => ({ skillId, importance, minYears, weight }))

export function useJobMutations() {
  const queryClient = useQueryClient()
  const refresh = () => Promise.all([queryClient.invalidateQueries({ queryKey: ['jobs'] }), queryClient.invalidateQueries({ queryKey: ['pipeline'] })])
  return {
    create: useMutation({
      mutationFn: (input: { job: JobInput; skills: SkillRequirement[] }) =>
        api<JobView>('/api/v1/jobs', { method: 'POST', body: { ...input.job, skills: requirements(input.skills) } }),
      onSuccess: refresh,
    }),
    update: useMutation({
      mutationFn: async (input: { id: string; version: number; changes: Partial<JobInput>; skills: SkillRequirement[] }) => {
        await api<JobView>(`/api/v1/jobs/${input.id}`, { method: 'PATCH', body: { ...input.changes, version: input.version } })
        return api<JobView>(`/api/v1/jobs/${input.id}/skills`, { method: 'PUT', body: { skills: requirements(input.skills) } })
      },
      onSuccess: refresh,
    }),
    changeStatus: useMutation({
      mutationFn: (input: { id: string; toStatus: JobStatus }) =>
        api<JobView>(`/api/v1/jobs/${input.id}/status`, { method: 'POST', body: { toStatus: input.toStatus } }),
      onSuccess: refresh,
    }),
    createClient: useMutation({
      mutationFn: (input: { name: string; industry?: string; contactName?: string; contactEmail?: string }) =>
        api<ClientView>('/api/v1/clients', { method: 'POST', body: input }),
      onSuccess: () => queryClient.invalidateQueries({ queryKey: ['clients'] }),
    }),
    apply: useMutation({
      mutationFn: (input: { candidateId: string; jobId: string }) => api<ApplicationView>('/api/v1/applications', { method: 'POST', body: input }),
      onSuccess: refresh,
    }),
    move: useMutation({
      mutationFn: (input: { application: ApplicationView; to: ApplicationStatus; reason?: RejectionReason; comment?: string }) =>
        api<StatusChangeResult>(`/api/v1/applications/${input.application.id}/status`, {
          method: 'POST',
          body: { toStatus: input.to, reason: input.reason, comment: input.comment, version: input.application.version },
        }),
      onSuccess: refresh,
    }),
  }
}
