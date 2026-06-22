import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateAccountRequest, UpdateAccountRequest } from '@/api'
import { accountsApi } from './api'

export const accountKeys = {
  all: ['accounts'] as const,
  list: (includeArchived: boolean) => ['accounts', 'list', { includeArchived }] as const,
  balance: (id: number) => ['accounts', 'balance', id] as const,
}

export function useAccounts(includeArchived: boolean) {
  return useQuery({
    queryKey: accountKeys.list(includeArchived),
    queryFn: () => accountsApi.list(includeArchived),
  })
}

export function useAccountBalance(id: number, enabled: boolean) {
  return useQuery({
    queryKey: accountKeys.balance(id),
    queryFn: () => accountsApi.balance(id),
    enabled,
  })
}

export function useCreateAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateAccountRequest) => accountsApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.all }),
  })
}

export function useUpdateAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateAccountRequest }) => accountsApi.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.all }),
  })
}

export function useArchiveAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => accountsApi.archive(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.all }),
  })
}
