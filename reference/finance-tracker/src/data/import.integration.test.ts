import 'fake-indexeddb/auto'
import { beforeAll, describe, expect, it } from 'vitest'
import { db } from './db'
import { createAccount } from './accounts'
import { createCategory } from './categories'
import { createRule } from './rules'
import { commitImport, listImportBatches, undoImportBatch } from './import'
import { buildImportRows } from './importRows'
import type { ImportMapping } from './types'

const mapping: ImportMapping = {
  delimiter: ';',
  encoding: 'utf-8',
  hasHeader: true,
  dateIndex: 0,
  dateFormat: 'auto',
  descriptionIndex: 1,
  amountMode: 'signed',
  amountIndex: 2,
  expenseIsNegative: true,
  debitIndex: -1,
  creditIndex: -1,
}

const csvRows = [
  ['Date', 'Title', 'Amount'],
  ['15.05.2026', 'Płatność BIEDRONKA 4012', '-19,99'],
  ['16.05.2026', 'Pensja', '5 000,00'],
]

beforeAll(async () => {
  await db.open()
  await Promise.all([
    db.transactions.clear(),
    db.accounts.clear(),
    db.categories.clear(),
    db.rules.clear(),
    db.importBatches.clear(),
    db.importProfiles.clear(),
    db.settings.clear(),
  ])
})

describe('CSV import pipeline (real Dexie via fake-indexeddb)', () => {
  it('imports, auto-categorizes, dedupes on re-import, and undoes', async () => {
    const account = await createAccount({ name: 'Checking', type: 'checking', currency: 'PLN' })
    const groceries = await createCategory({ name: 'Groceries', kind: 'expense' })
    await createRule({ pattern: 'biedronka', categoryId: groceries.id, priority: 1 })

    // First import.
    const first = await commitImport({
      accountId: account.id,
      fileName: 'may.csv',
      rows: buildImportRows(csvRows, mapping),
      mapping,
    })
    expect(first.imported).toBe(2)
    expect(first.skippedDuplicates).toBe(0)

    const txs = await db.transactions.where('accountId').equals(account.id).toArray()
    expect(txs).toHaveLength(2)

    const biedronka = txs.find((t) => t.description.includes('BIEDRONKA'))
    expect(biedronka?.type).toBe('expense')
    expect(biedronka?.amountMinor).toBe(1999)
    expect(biedronka?.categoryId).toBe(groceries.id) // rule applied
    expect(biedronka?.importBatchId).toBe(first.batchId)

    const pensja = txs.find((t) => t.description === 'Pensja')
    expect(pensja?.type).toBe('income')
    expect(pensja?.amountMinor).toBe(500000)
    expect(pensja?.categoryId).toBeNull() // no matching rule

    // Re-importing the same file is fully deduplicated.
    const second = await commitImport({
      accountId: account.id,
      fileName: 'may.csv',
      rows: buildImportRows(csvRows, mapping),
      mapping,
    })
    expect(second.imported).toBe(0)
    expect(second.skippedDuplicates).toBe(2)

    // Only the first (non-empty) import produced a batch.
    expect(await listImportBatches()).toHaveLength(1)

    // The mapping is remembered for the account.
    const profile = await db.importProfiles.get(account.id)
    expect(profile?.amountMode).toBe('signed')

    // Undo removes the batch's transactions.
    const removed = await undoImportBatch(first.batchId)
    expect(removed).toBe(2)
    expect(await db.transactions.where('accountId').equals(account.id).count()).toBe(0)
    expect(await listImportBatches()).toHaveLength(0)
  })
})
