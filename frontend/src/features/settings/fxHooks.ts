import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api'
import type { FxRate, FxRates, UpsertFxRateRequest } from '@/api'

/**
 * The user's FX rate table. These rates feed transaction entry only — the backend copies one onto
 * a transaction at save time and freezes it, so mutating a rate never invalidates a report. That is
 * why nothing here touches the `reports` query cache.
 */
export const fxKeys = { all: ['fx', 'rates'] as const }

export function useFxRates() {
  return useQuery({
    queryKey: fxKeys.all,
    queryFn: () => api.get<FxRates>('/api/v1/fx/rates'),
  })
}

export function useUpsertFxRate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ currency, body }: { currency: string; body: UpsertFxRateRequest }) =>
      api.put<FxRate>(`/api/v1/fx/rates/${currency.toUpperCase()}`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: fxKeys.all })
    },
  })
}

export function useDeleteFxRate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (currency: string) => api.delete<void>(`/api/v1/fx/rates/${currency.toUpperCase()}`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: fxKeys.all })
    },
  })
}
