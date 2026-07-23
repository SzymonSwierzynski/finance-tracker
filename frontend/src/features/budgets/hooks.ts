import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateBudgetRequest, UpdateBudgetRequest } from '@/api'
import { budgetsApi } from './api'

export const budgetKeys = {
  all: ['budgets'] as const,
  month: (month: string) => ['budgets', month] as const,
}

export function useBudgets(month: string) {
  return useQuery({ queryKey: budgetKeys.month(month), queryFn: () => budgetsApi.list(month) })
}

/** Progress depends on the month, so invalidate every budgets query (all months) on any change. */
function invalidateBudgets(qc: ReturnType<typeof useQueryClient>) {
  return qc.invalidateQueries({ queryKey: budgetKeys.all })
}

export function useCreateBudget() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateBudgetRequest) => budgetsApi.create(body),
    onSuccess: () => invalidateBudgets(qc),
  })
}

export function useUpdateBudget() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateBudgetRequest }) =>
      budgetsApi.update(id, body),
    onSuccess: () => invalidateBudgets(qc),
  })
}

export function useDeleteBudget() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => budgetsApi.remove(id),
    onSuccess: () => invalidateBudgets(qc),
  })
}
