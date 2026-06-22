import type { TokenResponse } from './types'

/**
 * The single typed fetch client (CLAUDE.md §11). It attaches the in-memory access token,
 * transparently refreshes once on a 401 (the refresh token rides in an HttpOnly cookie), and maps
 * RFC 9457 problem+json error bodies to {@link ApiError}. Feature `api.ts` modules call this; UI
 * code calls hooks, never fetch directly.
 */

const BASE = import.meta.env.VITE_API_BASE_URL ?? ''

/** Access token lives only in memory to limit XSS blast radius (never localStorage). */
let accessToken: string | null = null
export function setAccessToken(token: string | null): void {
  accessToken = token
}
export function getAccessToken(): string | null {
  return accessToken
}

export interface FieldError {
  field: string
  message: string
}

/** Normalized error shape surfaced to the UI. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, title: string, detail: string, fieldErrors: FieldError[] = []) {
    super(detail || title || `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.fieldErrors = fieldErrors
  }
}

interface RequestOptions {
  body?: unknown
  params?: Record<string, string | number | boolean | undefined | null>
  signal?: AbortSignal
  /** Auth endpoints must not trigger the 401 refresh-retry loop. */
  skipAuthRefresh?: boolean
}

// De-dupe concurrent refreshes: many in-flight requests share one refresh attempt.
let refreshInFlight: Promise<TokenResponse | null> | null = null

export async function refreshSession(): Promise<TokenResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${BASE}/api/v1/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
        })
        if (!res.ok) return null
        const data = (await res.json()) as TokenResponse
        accessToken = data.accessToken
        return data
      } catch {
        return null
      } finally {
        // Cleared on the next tick so awaiting callers all observe the same result first.
        setTimeout(() => {
          refreshInFlight = null
        }, 0)
      }
    })()
  }
  return refreshInFlight
}

function buildUrl(path: string, params?: RequestOptions['params']): string {
  const url = `${BASE}${path}`
  if (!params) return url
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, String(value))
    }
  }
  const qs = search.toString()
  return qs ? `${url}?${qs}` : url
}

async function toApiError(res: Response): Promise<ApiError> {
  let title = res.statusText
  let detail = ''
  let fieldErrors: FieldError[] = []
  try {
    const body = await res.json()
    if (body && typeof body === 'object') {
      title = typeof body.title === 'string' ? body.title : title
      detail = typeof body.detail === 'string' ? body.detail : detail
      if (Array.isArray(body.errors)) {
        fieldErrors = body.errors
          .filter((e: unknown): e is FieldError => !!e && typeof e === 'object')
          .map((e: { field?: string; message?: string }) => ({
            field: e.field ?? '',
            message: e.message ?? 'invalid',
          }))
      }
    }
  } catch {
    // Non-JSON error body; keep the status text.
  }
  return new ApiError(res.status, title, detail, fieldErrors)
}

async function request<T>(method: string, path: string, opts: RequestOptions = {}): Promise<T> {
  const url = buildUrl(path, opts.params)
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'

  const send = (): Promise<Response> => {
    const finalHeaders: Record<string, string> = { ...headers }
    if (accessToken) finalHeaders.Authorization = `Bearer ${accessToken}`
    return fetch(url, {
      method,
      headers: finalHeaders,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      credentials: 'include',
      signal: opts.signal,
    })
  }

  let res = await send()
  if (res.status === 401 && !opts.skipAuthRefresh) {
    const refreshed = await refreshSession()
    if (refreshed) res = await send()
  }

  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(path: string, opts?: RequestOptions) => request<T>('GET', path, opts),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>('POST', path, { ...opts, body }),
  put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>('PUT', path, { ...opts, body }),
  patch: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>('PATCH', path, { ...opts, body }),
  delete: <T>(path: string, opts?: RequestOptions) => request<T>('DELETE', path, opts),
}
