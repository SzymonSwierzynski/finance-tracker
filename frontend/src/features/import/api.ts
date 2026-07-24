import { api } from '@/api'
import type { CommitResult, ImportBatch, ImportMapping, PreviewResponse } from '@/api'

/** Multipart body: the CSV file plus the mapping as a JSON part (so @RequestPart deserializes it). */
function form(file: File, mapping: ImportMapping): FormData {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }))
  return fd
}

export const importApi = {
  preview: (accountId: number, file: File, mapping: ImportMapping) =>
    api.post<PreviewResponse>('/api/v1/imports/preview', form(file, mapping), {
      params: { accountId },
    }),
  commit: (accountId: number, file: File, mapping: ImportMapping) =>
    api.post<CommitResult>('/api/v1/imports/commit', form(file, mapping), { params: { accountId } }),
  batches: () => api.get<ImportBatch[]>('/api/v1/imports/batches'),
  undoBatch: (id: number) => api.delete<void>(`/api/v1/imports/batches/${id}`),
  getProfile: (accountId: number) => api.get<ImportMapping>(`/api/v1/imports/profiles/${accountId}`),
}
