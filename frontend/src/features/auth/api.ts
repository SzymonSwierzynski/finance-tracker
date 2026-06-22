import { api } from '@/api'
import type { LoginRequest, RegisterRequest, TokenResponse } from '@/api'

/** Auth endpoints. These skip the 401 refresh-retry (they ARE the auth flow). */
export const authApi = {
  login: (body: LoginRequest) =>
    api.post<TokenResponse>('/api/v1/auth/login', body, { skipAuthRefresh: true }),
  register: (body: RegisterRequest) =>
    api.post<TokenResponse>('/api/v1/auth/register', body, { skipAuthRefresh: true }),
  logout: () => api.post<void>('/api/v1/auth/logout', undefined, { skipAuthRefresh: true }),
}
