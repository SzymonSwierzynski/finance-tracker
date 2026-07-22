import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateRuleRequest, UpdateRuleRequest } from '@/api'
import { rulesApi } from './api'

export const ruleKeys = { all: ['rules'] as const }

export function useRules() {
  return useQuery({ queryKey: ruleKeys.all, queryFn: () => rulesApi.list() })
}

export function useCreateRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateRuleRequest) => rulesApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ruleKeys.all }),
  })
}

export function useUpdateRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateRuleRequest }) => rulesApi.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ruleKeys.all }),
  })
}

export function useDeleteRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => rulesApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ruleKeys.all }),
  })
}

export function useApplyRules() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => rulesApi.apply(),
    onSuccess: () => {
      // Applying rules re-categorizes transactions, shifting lists and reports.
      void qc.invalidateQueries({ queryKey: ['transactions'] })
      void qc.invalidateQueries({ queryKey: ['reports'] })
    },
  })
}
