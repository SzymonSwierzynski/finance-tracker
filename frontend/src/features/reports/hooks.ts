import { useQuery } from '@tanstack/react-query'
import { api } from '@/api'
import type { Summary } from '@/api'

export const reportKeys = {
  summary: (from: string, to: string) => ['reports', 'summary', { from, to }] as const,
}

export function useSummary(from: string, to: string) {
  return useQuery({
    queryKey: reportKeys.summary(from, to),
    queryFn: () => api.get<Summary>('/api/v1/reports/summary', { params: { from, to } }),
  })
}
