import { api } from '@/api'
import type {
  CreateTransactionRequest,
  Page,
  Transaction,
  TransactionType,
  UpdateTransactionRequest,
} from '@/api'

export interface TransactionFilters {
  from?: string
  to?: string
  accountId?: number
  type?: TransactionType
  categoryId?: number
  q?: string
  sort?: string
  page?: number
  size?: number
}

export const transactionsApi = {
  list: (filters: TransactionFilters) =>
    api.get<Page<Transaction>>('/api/v1/transactions', { params: { ...filters } }),
  create: (body: CreateTransactionRequest, idempotencyKey?: string) =>
    api.post<Transaction>('/api/v1/transactions', body, { idempotencyKey }),
  update: (id: number, body: UpdateTransactionRequest) =>
    api.patch<Transaction>(`/api/v1/transactions/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/v1/transactions/${id}`),
  restore: (id: number) => api.post<Transaction>(`/api/v1/transactions/${id}/restore`),
  trash: (page = 0, size = 50) =>
    api.get<Page<Transaction>>('/api/v1/transactions/trash', { params: { page, size } }),
  permanent: (id: number) => api.delete<void>(`/api/v1/transactions/${id}/permanent`),
  bulkDelete: (ids: number[]) =>
    api.post<{ affected: number }>('/api/v1/transactions/bulk-delete', { ids }),
  bulkRestore: (ids: number[]) =>
    api.post<{ affected: number }>('/api/v1/transactions/bulk-restore', { ids }),
  bulkCategorize: (ids: number[], categoryId: number | null) =>
    api.post<{ affected: number }>('/api/v1/transactions/bulk-categorize', { ids, categoryId }),
}
