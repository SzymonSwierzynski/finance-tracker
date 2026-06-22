import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api'
import type { Settings, UpdateSettingsRequest } from '@/api'

export const settingsKeys = { all: ['settings'] as const }

export function useSettings() {
  return useQuery({
    queryKey: settingsKeys.all,
    queryFn: () => api.get<Settings>('/api/v1/settings'),
  })
}

export function useUpdateSettings() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateSettingsRequest) => api.put<Settings>('/api/v1/settings', body),
    onSuccess: (data) => {
      qc.setQueryData(settingsKeys.all, data)
      // Reporting currency drives report labels/rollups.
      void qc.invalidateQueries({ queryKey: ['reports'] })
    },
  })
}
