import { afterEach, expect, it, vi } from 'vitest'
import { api } from './client'

afterEach(() => vi.restoreAllMocks())

it('sends the Idempotency-Key header when provided', async () => {
  const fetchMock = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response('{"ok":true}', { status: 201 }))

  await api.post('/api/v1/transactions', { amountMinor: 1 }, { idempotencyKey: 'abc-123' })

  const init = fetchMock.mock.calls[0]?.[1]
  const headers = init?.headers as Record<string, string> | undefined
  expect(headers?.['Idempotency-Key']).toBe('abc-123')
})

it('omits the header when no key is given', async () => {
  const fetchMock = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response('{"ok":true}', { status: 201 }))

  await api.post('/api/v1/transactions', { amountMinor: 1 })

  const init = fetchMock.mock.calls[0]?.[1]
  const headers = init?.headers as Record<string, string> | undefined
  expect(headers?.['Idempotency-Key']).toBeUndefined()
})
