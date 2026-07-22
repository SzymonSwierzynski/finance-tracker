import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateRecurringRequest, UpdateRecurringRequest } from '@/api'
import { recurringApi } from './api'

export const recurringKeys = { all: ['recurring'] as const }

export function useRecurring() {
  return useQuery({ queryKey: recurringKeys.all, queryFn: () => recurringApi.list() })
}

export function useCreateRecurring() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateRecurringRequest) => recurringApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: recurringKeys.all }),
  })
}

export function useUpdateRecurring() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateRecurringRequest }) =>
      recurringApi.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: recurringKeys.all }),
  })
}

export function useDeleteRecurring() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => recurringApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: recurringKeys.all }),
  })
}

export function useRunRecurring() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => recurringApi.run(),
    onSuccess: () => {
      // Materialization creates transactions -> refresh templates, lists and reports.
      void qc.invalidateQueries({ queryKey: recurringKeys.all })
      void qc.invalidateQueries({ queryKey: ['transactions'] })
      void qc.invalidateQueries({ queryKey: ['reports'] })
    },
  })
}
