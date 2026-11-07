import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../lib/apiClient'
import type { FeedbackList, FeedbackView, InterviewStatus, InterviewType, InterviewView, Interviewer, PageResponse, Recommendation } from '../../types'

export function useMyInterviews(scope: 'upcoming' | 'past' | 'all') {
  return useQuery({
    queryKey: ['interviews', 'mine', scope],
    queryFn: ({ signal }) => api<InterviewView[]>('/api/v1/interviews/mine', { query: { scope }, signal }),
  })
}

export function useInterviews(filters: { from?: string; to?: string; status?: InterviewStatus | ''; sort: string }) {
  return useQuery({
    queryKey: ['interviews', 'list', filters],
    queryFn: ({ signal }) => api<PageResponse<InterviewView>>('/api/v1/interviews', { query: { ...filters, size: 50 }, signal }),
  })
}

export function useInterview(id: string) {
  return useQuery({
    queryKey: ['interviews', 'detail', id],
    queryFn: ({ signal }) => api<InterviewView>(`/api/v1/interviews/${id}`, { signal }),
  })
}

export function useFeedback(id: string) {
  return useQuery({
    queryKey: ['interviews', 'feedback', id],
    queryFn: ({ signal }) => api<FeedbackList>(`/api/v1/interviews/${id}/feedback`, { signal }),
  })
}

export function useInterviewers(enabled = true) {
  return useQuery({
    queryKey: ['interviewers'],
    queryFn: ({ signal }) => api<Interviewer[]>('/api/v1/interviews/interviewers', { signal }),
    enabled,
    staleTime: 60_000,
  })
}

export interface ScheduleInput {
  applicationId: string
  type: InterviewType
  start: string
  end: string
  locationOrLink?: string
  participants: { userId: string; role: 'LEAD' | 'PANEL' }[]
}

export function useInterviewMutations() {
  const queryClient = useQueryClient()
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['interviews'] }),
      queryClient.invalidateQueries({ queryKey: ['pipeline'] }),
    ])
  return {
    schedule: useMutation({
      mutationFn: (input: ScheduleInput) => api<InterviewView>('/api/v1/interviews', { method: 'POST', body: input }),
      onSuccess: refresh,
    }),
    finish: useMutation({
      mutationFn: (input: { id: string; action: 'cancel' | 'complete' }) =>
        api<InterviewView>(`/api/v1/interviews/${input.id}/${input.action}`, { method: 'POST' }),
      onSuccess: refresh,
    }),
    submitFeedback: useMutation({
      mutationFn: (input: { id: string; rating: number; recommendation: Recommendation; strengths?: string; concerns?: string }) =>
        api<FeedbackView>(`/api/v1/interviews/${input.id}/feedback`, {
          method: 'POST',
          body: { rating: input.rating, recommendation: input.recommendation, strengths: input.strengths, concerns: input.concerns },
        }),
      onSuccess: refresh,
    }),
  }
}
