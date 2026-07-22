import { useQuery } from '@tanstack/react-query'
import { api } from '@/api'
import type {
  Breakdown,
  CashflowResponse,
  CategoryKind,
  CategoryTrendResponse,
  Summary,
  TrendResponse,
} from '@/api'

export const reportKeys = {
  summary: (from: string, to: string) => ['reports', 'summary', { from, to }] as const,
  breakdown: (from: string, to: string, kind: CategoryKind) =>
    ['reports', 'breakdown', { from, to, kind }] as const,
  trend: (from: string, to: string, interval: string) =>
    ['reports', 'trend', { from, to, interval }] as const,
  cashflow: (from: string, to: string, interval: string) =>
    ['reports', 'cashflow', { from, to, interval }] as const,
  categoryTrend: (from: string, to: string, interval: string, kind: CategoryKind) =>
    ['reports', 'category-trend', { from, to, interval, kind }] as const,
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

export function useTrend(from: string, to: string, interval: string) {
  return useQuery({
    queryKey: reportKeys.trend(from, to, interval),
    queryFn: () =>
      api.get<TrendResponse>('/api/v1/reports/trend', { params: { from, to, interval } }),
  })
}

export function useCashflow(from: string, to: string, interval: string, enabled = true) {
  return useQuery({
    enabled,
    queryKey: reportKeys.cashflow(from, to, interval),
    queryFn: () =>
      api.get<CashflowResponse>('/api/v1/reports/cashflow', { params: { from, to, interval } }),
  })
}

export function useCategoryTrend(
  from: string,
  to: string,
  interval: string,
  kind: CategoryKind,
  enabled = true,
) {
  return useQuery({
    enabled,
    queryKey: reportKeys.categoryTrend(from, to, interval, kind),
    queryFn: () =>
      api.get<CategoryTrendResponse>('/api/v1/reports/category-trend', {
        params: { from, to, interval, kind },
      }),
  })
}
