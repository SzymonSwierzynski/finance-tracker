import { api } from '@/api'
import type { BudgetResponse, Budgets, CreateBudgetRequest, UpdateBudgetRequest } from '@/api'

export const budgetsApi = {
  list: (month: string) => api.get<Budgets>(`/api/v1/budgets?month=${month}`),
  create: (body: CreateBudgetRequest) => api.post<BudgetResponse>('/api/v1/budgets', body),
  update: (id: number, body: UpdateBudgetRequest) =>
    api.patch<BudgetResponse>(`/api/v1/budgets/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/v1/budgets/${id}`),
}
