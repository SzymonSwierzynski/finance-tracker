import { api } from '@/api'
import type {
  Category,
  CategoryKind,
  CreateCategoryRequest,
  DeleteCategoryResult,
  UpdateCategoryRequest,
} from '@/api'

export const categoriesApi = {
  list: (kind?: CategoryKind) => api.get<Category[]>('/api/v1/categories', { params: { kind } }),
  create: (body: CreateCategoryRequest) => api.post<Category>('/api/v1/categories', body),
  update: (id: number, body: UpdateCategoryRequest) =>
    api.patch<Category>(`/api/v1/categories/${id}`, body),
  remove: (id: number) => api.delete<DeleteCategoryResult>(`/api/v1/categories/${id}`),
}
