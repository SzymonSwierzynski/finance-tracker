import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateTransactionRequest, UpdateTransactionRequest } from '@/api'
import { transactionsApi } from './api'
import type { TransactionFilters } from './api'

export const transactionKeys = {
  all: ['transactions'] as const,
  list: (filters: TransactionFilters) => ['transactions', 'list', filters] as const,
}

/** Invalidate everything that derives from transactions (lists, reports, balances). */
function invalidateDerived(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ['transactions'] })
  void qc.invalidateQueries({ queryKey: ['reports'] })
  void qc.invalidateQueries({ queryKey: ['accounts', 'balance'] })
}

export function useTransactions(filters: TransactionFilters) {
  return useQuery({
    queryKey: transactionKeys.list(filters),
    queryFn: () => transactionsApi.list(filters),
  })
}

export function useCreateTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTransactionRequest) =>
      transactionsApi.create(body, crypto.randomUUID()),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function useUpdateTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateTransactionRequest }) =>
      transactionsApi.update(id, body),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function useDeleteTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => transactionsApi.remove(id),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function useTrash(page = 0, size = 50) {
  return useQuery({
    queryKey: ['transactions', 'trash', page, size] as const,
    queryFn: () => transactionsApi.trash(page, size),
  })
}

export function useRestoreTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => transactionsApi.restore(id),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function usePermanentlyDeleteTransaction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => transactionsApi.permanent(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['transactions', 'trash'] }),
  })
}

export function useBulkDelete() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => transactionsApi.bulkDelete(ids),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function useBulkRestore() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => transactionsApi.bulkRestore(ids),
    onSuccess: () => invalidateDerived(qc),
  })
}

export function useBulkCategorize() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ ids, categoryId }: { ids: number[]; categoryId: number | null }) =>
      transactionsApi.bulkCategorize(ids, categoryId),
    onSuccess: () => invalidateDerived(qc),
  })
}
