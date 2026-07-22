import { getAccessToken, refreshSession } from '@/api'

// Exports aren't JSON (CSV) and are downloaded as files, so they bypass the shared JSON client and
// do their own authed fetch (with one refresh-retry on 401), then trigger a browser download.
const BASE = import.meta.env.VITE_API_BASE_URL ?? ''

async function authedFetch(path: string, accept: string): Promise<Response> {
  const init = (): RequestInit => ({
    headers: { Authorization: `Bearer ${getAccessToken() ?? ''}`, Accept: accept },
    credentials: 'include',
  })
  let res = await fetch(`${BASE}${path}`, init())
  if (res.status === 401) {
    await refreshSession()
    res = await fetch(`${BASE}${path}`, init())
  }
  return res
}

async function download(path: string, filename: string, accept: string): Promise<void> {
  const res = await authedFetch(path, accept)
  if (!res.ok) throw new Error(`Export failed (${res.status})`)
  const url = URL.createObjectURL(await res.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export const exportApi = {
  csv: () => download('/api/v1/export/transactions/csv', 'transactions.csv', 'text/csv'),
  json: () => download('/api/v1/export/transactions', 'transactions.json', 'application/json'),
}
