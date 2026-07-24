import { api } from '@/api'
import type { CreateRecurringRequest, Recurring, UpdateRecurringRequest } from '@/api'

export const recurringApi = {
  list: () => api.get<Recurring[]>('/api/v1/recurring'),
  create: (body: CreateRecurringRequest) => api.post<Recurring>('/api/v1/recurring', body),
  update: (id: number, body: UpdateRecurringRequest) =>
    api.patch<Recurring>(`/api/v1/recurring/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/v1/recurring/${id}`),
  run: () => api.post<{ materialized: number }>('/api/v1/recurring/run'),
}
