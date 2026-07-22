import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api'

/**
 * Outcome of POST /api/v1/export/restore. Mirrors the backend RestoreSummary DTO; run
 * `npm run gen:api` to formalize it from the OpenAPI spec once the backend is running.
 */
export type RestoreSummary = {
  accountsCreated: number
  categoriesCreated: number
  transactionsImported: number
  transactionsSkipped: number
  transfersSkipped: number
}

/**
 * Restore a full backup. The body is the parsed backup JSON (posted as-is). A restore can touch
 * accounts, categories, transactions, recurring templates and reports, so on success we invalidate
 * every query and let the UI refetch what it needs.
 */
export function useRestore() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (backup: unknown) => api.post<RestoreSummary>('/api/v1/export/restore', backup),
    onSuccess: () => qc.invalidateQueries(),
  })
}
