import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../lib/errors'

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // A 4xx will not fix itself on retry; only transient failures are worth another attempt.
        retry: (failureCount, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
        refetchOnWindowFocus: false,
        staleTime: 15_000,
      },
    },
  })
}
