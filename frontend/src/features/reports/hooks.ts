import { useQuery } from '@tanstack/react-query'
import { api } from '@/api'
import type { Breakdown, CategoryKind, Summary } from '@/api'

export const reportKeys = {
  summary: (from: string, to: string) => ['reports', 'summary', { from, to }] as const,
  breakdown: (from: string, to: string, kind: CategoryKind) =>
    ['reports', 'breakdown', { from, to, kind }] as const,
}

export function useSummary(from: string, to: string) {
  return useQuery({
    queryKey: reportKeys.summary(from, to),
    queryFn: () => api.get<Summary>('/api/v1/reports/summary', { params: { from, to } }),
  })
}

export function useBreakdown(from: string, to: string, kind: CategoryKind) {
  return useQuery({
    queryKey: reportKeys.breakdown(from, to, kind),
    queryFn: () => api.get<Breakdown>('/api/v1/reports/breakdown', { params: { from, to, kind } }),
  })
}
