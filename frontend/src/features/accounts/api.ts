import { api } from '@/api'
import type { Account, AccountBalance, CreateAccountRequest, UpdateAccountRequest } from '@/api'

export const accountsApi = {
  list: (includeArchived: boolean) =>
    api.get<Account[]>('/api/v1/accounts', { params: { includeArchived } }),
  get: (id: number) => api.get<Account>(`/api/v1/accounts/${id}`),
  create: (body: CreateAccountRequest) => api.post<Account>('/api/v1/accounts', body),
  update: (id: number, body: UpdateAccountRequest) => api.patch<Account>(`/api/v1/accounts/${id}`, body),
  archive: (id: number) => api.post<void>(`/api/v1/accounts/${id}/archive`),
  balance: (id: number) => api.get<AccountBalance>(`/api/v1/accounts/${id}/balance`),
}
