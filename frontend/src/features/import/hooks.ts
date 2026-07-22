import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ImportMapping } from '@/api'
import { importApi } from './api'

export const importKeys = { batches: ['imports', 'batches'] as const }

interface ImportArgs {
  accountId: number
  file: File
  mapping: ImportMapping
}

/** Everything an import (or its undo) changes: transactions, reports, balances, and the batch list. */
function invalidateImported(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: importKeys.batches })
  void qc.invalidateQueries({ queryKey: ['transactions'] })
  void qc.invalidateQueries({ queryKey: ['reports'] })
  void qc.invalidateQueries({ queryKey: ['accounts', 'balance'] })
}

export function useImportBatches() {
  return useQuery({ queryKey: importKeys.batches, queryFn: () => importApi.batches() })
}

export function usePreviewImport() {
  return useMutation({
    mutationFn: ({ accountId, file, mapping }: ImportArgs) =>
      importApi.preview(accountId, file, mapping),
  })
}

export function useCommitImport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ accountId, file, mapping }: ImportArgs) =>
      importApi.commit(accountId, file, mapping),
    onSuccess: () => invalidateImported(qc),
  })
}

export function useUndoImport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => importApi.undoBatch(id),
    onSuccess: () => invalidateImported(qc),
  })
}
