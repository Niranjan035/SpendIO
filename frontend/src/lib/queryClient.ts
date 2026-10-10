import { QueryClient } from '@tanstack/react-query'

export const STALE_TIME_MS = 30_000

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: STALE_TIME_MS,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          const status = (error as { status?: number }).status
          if (typeof status === 'number' && status >= 400 && status < 500) return false
          return failureCount < 2
        },
      },
      mutations: { retry: false },
    },
  })
}
