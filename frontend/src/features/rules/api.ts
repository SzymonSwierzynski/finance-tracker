import { api } from '@/api'
import type { ApplyRulesResult, CreateRuleRequest, Rule, UpdateRuleRequest } from '@/api'

export const rulesApi = {
  list: () => api.get<Rule[]>('/api/v1/rules'),
  create: (body: CreateRuleRequest) => api.post<Rule>('/api/v1/rules', body),
  update: (id: number, body: UpdateRuleRequest) => api.patch<Rule>(`/api/v1/rules/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/v1/rules/${id}`),
  apply: () => api.post<ApplyRulesResult>('/api/v1/rules/apply'),
}
