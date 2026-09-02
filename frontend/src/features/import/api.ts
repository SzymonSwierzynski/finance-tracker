import { api } from '@/api'
import type { CommitResult, ImportBatch, ImportMapping, PreviewResponse } from '@/api'

/**
 * Multipart body: the CSV file plus, when supplied, the mapping as a JSON part (so @RequestPart
 * deserializes it). A null mapping omits the part entirely, which tells the backend to auto-detect.
 */
function form(file: File, mapping: ImportMapping | null): FormData {
  const fd = new FormData()
  fd.append('file', file)
  if (mapping) {
    fd.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }))
  }
  return fd
}

export const importApi = {
  preview: (accountId: number, file: File, mapping: ImportMapping | null) =>
    api.post<PreviewResponse>('/api/v1/imports/preview', form(file, mapping), {
      params: { accountId },
    }),
  commit: (accountId: number, file: File, mapping: ImportMapping | null, idempotencyKey?: string) =>
    api.post<CommitResult>('/api/v1/imports/commit', form(file, mapping), {
      params: { accountId },
      idempotencyKey,
    }),
  batches: () => api.get<ImportBatch[]>('/api/v1/imports/batches'),
  undoBatch: (id: number) => api.delete<void>(`/api/v1/imports/batches/${id}`),
  getProfile: (accountId: number) => api.get<ImportMapping>(`/api/v1/imports/profiles/${accountId}`),
}
