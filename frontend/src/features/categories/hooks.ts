import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CategoryKind, CreateCategoryRequest, UpdateCategoryRequest } from '@/api'
import { categoriesApi } from './api'

export const categoryKeys = {
  all: ['categories'] as const,
  list: (kind?: CategoryKind) => ['categories', 'list', { kind: kind ?? null }] as const,
}

export function useCategories(kind?: CategoryKind) {
  return useQuery({
    queryKey: categoryKeys.list(kind),
    queryFn: () => categoriesApi.list(kind),
  })
}

export function useCreateCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCategoryRequest) => categoriesApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: categoryKeys.all }),
  })
}

export function useUpdateCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateCategoryRequest }) =>
      categoriesApi.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: categoryKeys.all }),
  })
}

export function useDeleteCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => categoriesApi.remove(id),
    onSuccess: () => {
      // Deleting a category (and any subcategories) uncategorizes transactions and shifts reports.
      void qc.invalidateQueries({ queryKey: categoryKeys.all })
      void qc.invalidateQueries({ queryKey: ['transactions'] })
      void qc.invalidateQueries({ queryKey: ['reports'] })
    },
  })
}
