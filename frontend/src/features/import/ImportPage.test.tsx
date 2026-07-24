import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Account, CommitResult, PreviewResponse } from '@/api'
import { renderWithProviders } from '@/test/test-utils'
import { ImportPage } from './ImportPage'
import { importApi } from './api'
import { accountsApi } from '@/features/accounts/api'

vi.mock('./api', () => ({
  importApi: {
    preview: vi.fn(),
    commit: vi.fn(),
    batches: vi.fn(),
    undoBatch: vi.fn(),
    getProfile: vi.fn(),
  },
}))
vi.mock('@/features/accounts/api', () => ({
  accountsApi: { list: vi.fn(), balance: vi.fn(), create: vi.fn(), update: vi.fn(), archive: vi.fn() },
}))

const account: Account = {
  id: 1,
  name: 'mBank',
  type: 'checking',
  currency: 'PLN',
  startingBalanceMinor: null,
  trackBalance: false,
  archived: false,
  version: 0,
}

const previewResponse: PreviewResponse = {
  delimiter: ';',
  misdecoded: false,
  totalRows: 2,
  validRows: 2,
  duplicateRows: 1,
  rows: [
    {
      index: 1,
      date: '2026-05-15',
      amountMinor: 1999,
      type: 'expense',
      description: 'BIEDRONKA 4021',
      valid: true,
      error: null,
      duplicate: false,
    },
    {
      index: 2,
      date: '2026-05-16',
      amountMinor: 500000,
      type: 'income',
      description: 'PENSJA',
      valid: true,
      error: null,
      duplicate: true,
    },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(accountsApi.list).mockResolvedValue([account])
  vi.mocked(importApi.batches).mockResolvedValue([])
  // No saved profile for the account -> the wizard keeps the default mapping.
  vi.mocked(importApi.getProfile).mockRejectedValue(new Error('no profile'))
})

describe('<ImportPage /> wizard', () => {
  it('walks account+file -> map -> preview (with flags) -> commit', async () => {
    const user = userEvent.setup()
    vi.mocked(importApi.preview).mockResolvedValue(previewResponse)
    const commitResult: CommitResult = {
      batchId: 1,
      imported: 1,
      skippedDuplicates: 1,
      skippedInvalid: 0,
    }
    vi.mocked(importApi.commit).mockResolvedValue(commitResult)

    renderWithProviders(<ImportPage />)

    // Step 1: choose account (wait for the accounts query to populate the options) + file.
    await screen.findByRole('option', { name: /mBank/i })
    await user.selectOptions(screen.getByLabelText('Account'), '1')
    const file = new File(['Date;Desc;Amount\n15.05.2026;BIEDRONKA;-19,99'], 'export.csv', {
      type: 'text/csv',
    })
    await user.upload(screen.getByLabelText(/csv file/i), file)
    await user.click(screen.getByRole('button', { name: /next/i }))

    // Step 2: run the preview.
    await user.click(await screen.findByRole('button', { name: /^preview$/i }))

    // Step 3: rows render with their valid/duplicate flags.
    expect(await screen.findByText(/BIEDRONKA/)).toBeInTheDocument()
    expect(screen.getByText('PENSJA')).toBeInTheDocument()
    expect(screen.getByText('Duplicate')).toBeInTheDocument()

    // Commit.
    await user.click(screen.getByRole('button', { name: /^import$/i }))
    expect(await screen.findByText(/imported 1/i)).toBeInTheDocument()
    expect(importApi.commit).toHaveBeenCalledWith(
      1,
      expect.any(File),
      expect.objectContaining({ amountMode: 'signed' }),
    )
  })
})
