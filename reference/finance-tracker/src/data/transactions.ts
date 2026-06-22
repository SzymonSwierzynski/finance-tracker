import { startOfMonth, endOfMonth, format, parseISO } from 'date-fns'
import { toBaseMinor } from '@/lib/money'
import type { DateRange } from '@/lib/period'
import { db } from './db'
import { newId } from './ids'
import { dedupeHash } from './hash'
import { getSettings, resolveRateToBase } from './settings'
import { computeBreakdown } from './breakdown'
import type { CategoryBreakdown } from './breakdown'
import type { CategoryKind, Transaction, TransactionType } from './types'

export interface CreateTransactionInput {
  /** 'YYYY-MM-DD' */
  date: string
  /** Positive integer, native-currency minor units. */
  amountMinor: number
  type: TransactionType
  accountId: string
  counterAccountId?: string | null
  categoryId?: string | null
  currency: string
  /** Optional; defaults from settings (1 for the reporting currency). */
  rateToBase?: number
  description?: string
  note?: string
}

export async function createTransaction(input: CreateTransactionInput): Promise<Transaction> {
  const account = await db.accounts.get(input.accountId)
  if (!account) throw new Error(`Unknown account: ${input.accountId}`)

  if (!Number.isInteger(input.amountMinor) || input.amountMinor <= 0) {
    throw new Error('amountMinor must be a positive integer (minor units)')
  }

  // Resolve and lock the rate to base at entry time.
  let rateToBase = input.rateToBase
  if (rateToBase == null) {
    const settings = await getSettings()
    const resolved = resolveRateToBase(settings, input.currency)
    if (resolved == null) {
      throw new Error(`No exchange rate to base for ${input.currency}. Provide rateToBase.`)
    }
    rateToBase = resolved
  }

  const date = input.date
  const description = (input.description ?? '').trim()
  const isTransfer = input.type === 'transfer'

  const tx: Transaction = {
    id: newId(),
    date,
    amountMinor: input.amountMinor,
    type: input.type,
    accountId: input.accountId,
    counterAccountId: isTransfer ? input.counterAccountId ?? null : null,
    categoryId: isTransfer ? null : input.categoryId ?? null,
    currency: input.currency,
    rateToBase,
    description,
    note: (input.note ?? '').trim(),
    importBatchId: null,
    dedupeHash: dedupeHash([date, input.amountMinor, input.currency, input.accountId, description]),
  }
  await db.transactions.put(tx)
  return tx
}

export interface TransactionFilter {
  /** 'YYYY-MM' — restrict to a single month (takes precedence over start/end). */
  month?: string
  /** Inclusive 'YYYY-MM-DD' range bounds (used when month is absent). */
  start?: string
  end?: string
  accountId?: string
  /** Exact match, including null for uncategorized. Omit for any. */
  categoryId?: string | null
  type?: TransactionType
}

export async function listTransactions(filter: TransactionFilter = {}): Promise<Transaction[]> {
  const bounds = filter.month
    ? monthBounds(filter.month)
    : filter.start != null && filter.end != null
      ? { start: filter.start, end: filter.end }
      : null

  let rows = bounds
    ? await db.transactions.where('date').between(bounds.start, bounds.end, true, true).toArray()
    : await db.transactions.toArray()

  if (filter.accountId) {
    const id = filter.accountId
    rows = rows.filter((t) => t.accountId === id || t.counterAccountId === id)
  }
  if (filter.categoryId !== undefined) {
    rows = rows.filter((t) => t.categoryId === filter.categoryId)
  }
  if (filter.type) {
    rows = rows.filter((t) => t.type === filter.type)
  }
  // Newest date first; stable tie-break by id.
  rows.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : a.id < b.id ? 1 : -1))
  return rows
}

export async function getTransaction(id: string): Promise<Transaction | undefined> {
  return db.transactions.get(id)
}

export async function deleteTransaction(id: string): Promise<void> {
  await db.transactions.delete(id)
}

export interface MonthSummary {
  month: string
  /** All totals are in base (reporting) minor units. */
  incomeMinor: number
  expenseMinor: number
  netMinor: number
  count: number
}

/**
 * This-month income / expense / net. Every transaction is folded into base
 * (reporting) units via its locked rateToBase. Transfers are excluded — they
 * move money between accounts and are neither income nor expense.
 */
export async function getMonthSummary(month: string): Promise<MonthSummary> {
  const { start, end } = monthBounds(month)
  const rows = await db.transactions.where('date').between(start, end, true, true).toArray()

  let incomeMinor = 0
  let expenseMinor = 0
  for (const t of rows) {
    if (t.type === 'transfer') continue
    const base = toBaseMinor(t.amountMinor, t.rateToBase)
    if (t.type === 'income') incomeMinor += base
    else expenseMinor += base
  }

  return {
    month,
    incomeMinor,
    expenseMinor,
    netMinor: incomeMinor - expenseMinor,
    count: rows.length,
  }
}

/**
 * Category breakdown for a date range, computed in base (reporting) minor
 * units. Fetches the range's transactions plus all categories and folds them
 * via the pure computeBreakdown().
 */
export async function getCategoryBreakdown(
  range: DateRange,
  kind: CategoryKind,
): Promise<CategoryBreakdown> {
  const [txs, cats] = await Promise.all([
    db.transactions.where('date').between(range.start, range.end, true, true).toArray(),
    db.categories.toArray(),
  ])
  return computeBreakdown(txs, cats, kind)
}

/** Inclusive 'YYYY-MM-DD' bounds for a 'YYYY-MM' month. */
function monthBounds(month: string): { start: string; end: string } {
  const ref = parseISO(`${month}-01`)
  return {
    start: format(startOfMonth(ref), 'yyyy-MM-dd'),
    end: format(endOfMonth(ref), 'yyyy-MM-dd'),
  }
}
