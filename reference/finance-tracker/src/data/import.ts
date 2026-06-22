import { matchCategory } from '@/lib/rules'
import { db } from './db'
import { newId } from './ids'
import { dedupeHash } from './hash'
import { getSettings, resolveRateToBase } from './settings'
import type { ParsedImportRow } from './importRows'
import type { ImportBatch, ImportMapping, ImportProfile, Transaction } from './types'

export interface CommitImportInput {
  accountId: string
  fileName: string
  /** Parsed rows (invalid ones are skipped defensively). */
  rows: ParsedImportRow[]
  /** Saved as the account's remembered mapping when present. */
  mapping?: ImportMapping
}

export interface CommitImportResult {
  batchId: string
  imported: number
  skippedDuplicates: number
  skippedInvalid: number
}

/**
 * Import parsed rows as one undoable batch. Each row is converted to base via
 * the account's currency rate, auto-categorized through the rules engine, and
 * deduplicated by dedupeHash against existing rows (and within the batch).
 */
export async function commitImport(input: CommitImportInput): Promise<CommitImportResult> {
  const account = await db.accounts.get(input.accountId)
  if (!account) throw new Error(`Unknown account: ${input.accountId}`)

  const settings = await getSettings()
  const currency = account.currency
  const rateToBase = resolveRateToBase(settings, currency)
  if (rateToBase == null) throw new Error(`No exchange rate to base for ${currency}.`)

  const rules = await db.rules.toArray()
  const existing = await db.transactions.where('accountId').equals(input.accountId).toArray()
  const seen = new Set(existing.map((t) => t.dedupeHash))

  const batchId = newId()
  const toInsert: Transaction[] = []
  let skippedDuplicates = 0
  let skippedInvalid = 0

  for (const row of input.rows) {
    if (!row.valid || row.date == null || row.amountMinor == null) {
      skippedInvalid++
      continue
    }
    const hash = dedupeHash([row.date, row.amountMinor, currency, input.accountId, row.description])
    if (seen.has(hash)) {
      skippedDuplicates++
      continue
    }
    seen.add(hash)
    toInsert.push({
      id: newId(),
      date: row.date,
      amountMinor: row.amountMinor,
      type: row.type,
      accountId: input.accountId,
      counterAccountId: null,
      categoryId: matchCategory(row.description, rules),
      currency,
      rateToBase,
      description: row.description,
      note: '',
      importBatchId: batchId,
      dedupeHash: hash,
    })
  }

  if (toInsert.length > 0) {
    await db.transactions.bulkPut(toInsert)
    const batch: ImportBatch = {
      id: batchId,
      accountId: input.accountId,
      fileName: input.fileName,
      createdAt: new Date().toISOString(),
      count: toInsert.length,
    }
    await db.importBatches.put(batch)
  }

  if (input.mapping) {
    await saveImportProfile(input.accountId, input.mapping)
  }

  return { batchId, imported: toInsert.length, skippedDuplicates, skippedInvalid }
}

/** Import batches, newest first. */
export async function listImportBatches(): Promise<ImportBatch[]> {
  const all = await db.importBatches.toArray()
  return all.sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
}

/** Undo a batch: delete its transactions and the batch record. Returns count removed. */
export async function undoImportBatch(batchId: string): Promise<number> {
  const removed = await db.transactions.where('importBatchId').equals(batchId).delete()
  await db.importBatches.delete(batchId)
  return removed
}

export async function getImportProfile(accountId: string): Promise<ImportProfile | undefined> {
  return db.importProfiles.get(accountId)
}

export async function saveImportProfile(accountId: string, mapping: ImportMapping): Promise<void> {
  await db.importProfiles.put({ ...mapping, accountId })
}
