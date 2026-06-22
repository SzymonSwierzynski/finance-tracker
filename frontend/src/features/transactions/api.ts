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
  page?: number
  size?: number
}

export const transactionsApi = {
  list: (filters: TransactionFilters) =>
    api.get<Page<Transaction>>('/api/v1/transactions', { params: { ...filters } }),
  create: (body: CreateTransactionRequest) => api.post<Transaction>('/api/v1/transactions', body),
  update: (id: number, body: UpdateTransactionRequest) =>
    api.patch<Transaction>(`/api/v1/transactions/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/v1/transactions/${id}`),
}
