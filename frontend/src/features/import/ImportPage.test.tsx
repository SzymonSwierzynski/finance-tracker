import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Account, CommitResult, ImportMapping, PreviewResponse } from '@/api'
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

const detectedMapping: ImportMapping = {
  delimiter: ';',
  encoding: 'windows-1250',
  hasHeader: true,
  headerRowIndex: 5,
  dateIndex: 1,
  dateFormat: 'yyyy-MM-dd',
  descriptionIndex: 2,
  descriptionIndexes: [2, 3],
  amountMode: 'signed',
  amountIndex: 6,
  expenseIsNegative: true,
  debitIndex: -1,
  creditIndex: -1,
}

const previewResponse: PreviewResponse = {
  delimiter: ';',
  misdecoded: false,
  totalRows: 2,
  validRows: 2,
  duplicateRows: 1,
  incomeMinor: 500000,
  expenseMinor: 1999,
  mapping: detectedMapping,
  detection: {
    encoding: 'windows-1250',
    headerRowIndex: 5,
    recognizedColumns: { date: '#Data operacji', amount: '#Kwota', description: '#Opis operacji + #Tytuł' },
  },
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
})

describe('<ImportPage /> detect-first', () => {
  it('auto-detects on account+file, shows the banner, then commits the detected mapping', async () => {
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

    // Choose account (wait for the accounts query to populate the options) + file.
    await screen.findByRole('option', { name: /mBank/i })
    await user.selectOptions(screen.getByLabelText('Account'), '1')
    const file = new File(['Date;Desc;Amount\n15.05.2026;BIEDRONKA;-19,99'], 'export.csv', {
      type: 'text/csv',
    })
    await user.upload(screen.getByLabelText(/csv file/i), file)

    // Detect-first: preview runs automatically with no mapping.
    expect(await screen.findByText(/BIEDRONKA/)).toBeInTheDocument()
    expect(screen.getByText('PENSJA')).toBeInTheDocument()
    expect(screen.getByText('Duplicate')).toBeInTheDocument()
    expect(importApi.preview).toHaveBeenCalledWith(1, expect.any(File), null)

    // Detection banner reflects what the server concluded.
    expect(screen.getByText(/Detected/)).toBeInTheDocument()
    expect(screen.getByText(/windows-1250/)).toBeInTheDocument()

    // Commit sends the detected mapping.
    await user.click(screen.getByRole('button', { name: /^import$/i }))
    expect(await screen.findByText(/imported 1/i)).toBeInTheDocument()
    expect(importApi.commit).toHaveBeenCalledWith(
      1,
      expect.any(File),
      expect.objectContaining({ amountMode: 'signed', amountIndex: 6 }),
      expect.any(String), // per-submit Idempotency-Key
    )
  })

  it('re-previews with an explicit mapping when columns are adjusted', async () => {
    const user = userEvent.setup()
    vi.mocked(importApi.preview).mockResolvedValue(previewResponse)

    renderWithProviders(<ImportPage />)

    await screen.findByRole('option', { name: /mBank/i })
    await user.selectOptions(screen.getByLabelText('Account'), '1')
    const file = new File(['x'], 'export.csv', { type: 'text/csv' })
    await user.upload(screen.getByLabelText(/csv file/i), file)
    await screen.findByText(/BIEDRONKA/)

    // Open "Adjust columns", tweak the amount column, re-preview with the explicit mapping.
    await user.click(screen.getByRole('button', { name: /adjust columns/i }))
    const amountCol = screen.getByLabelText('Amount column')
    await user.clear(amountCol)
    await user.type(amountCol, '5')
    await user.click(screen.getByRole('button', { name: /^preview$/i }))

    expect(importApi.preview).toHaveBeenLastCalledWith(
      1,
      expect.any(File),
      expect.objectContaining({ amountIndex: 5 }),
    )
  })
})
