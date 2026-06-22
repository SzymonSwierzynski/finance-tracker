/**
 * Domain types — the single source of truth for the data model.
 * These mirror the schema in CLAUDE.md exactly.
 */

export type AccountType = 'checking' | 'savings' | 'cash' | 'credit'
export type CategoryKind = 'expense' | 'income'
export type TransactionType = 'expense' | 'income' | 'transfer'

export interface Account {
  id: string
  name: string
  type: AccountType
  currency: string
  /** Only meaningful when trackBalance is true. */
  startingBalanceMinor: number | null
  trackBalance: boolean
  archived: boolean
}

export interface Category {
  id: string
  name: string
  kind: CategoryKind
  /** Two levels only: parent categories have parentId === null. */
  parentId: string | null
  color: string
}

export interface Transaction {
  id: string
  /** ISO calendar date, 'YYYY-MM-DD' (sorts lexicographically for ranges). */
  date: string
  /** Positive integer, native-currency minor units. */
  amountMinor: number
  type: TransactionType
  accountId: string
  /** Transfers only; null otherwise. */
  counterAccountId: string | null
  categoryId: string | null
  currency: string
  /** base value = round(amountMinor * rateToBase). Locked at entry time. */
  rateToBase: number
  /** Raw text from the user/import. */
  description: string
  note: string
  importBatchId: string | null
  dedupeHash: string
}

export interface Rule {
  id: string
  /** Substring match on description. */
  pattern: string
  categoryId: string
  priority: number
}

export interface Settings {
  /** Singleton row key. */
  id: string
  reportingCurrency: string
  /** currency -> rate to base (reporting currency). */
  rates: Record<string, number>
}

export type AmountMode = 'signed' | 'debitCredit'

/** How to read a bank CSV. Columns are referenced by zero-based index (-1 = unset). */
export interface ImportMapping {
  /** PapaParse delimiter; '' = auto-detect. */
  delimiter: string
  /** Text decoding for the file bytes (e.g. 'utf-8', 'windows-1250'). */
  encoding: string
  /** First row is a header (shown as labels, not imported). */
  hasHeader: boolean
  dateIndex: number
  /** date-fns format, or 'auto' to try common Polish formats. */
  dateFormat: string
  descriptionIndex: number
  amountMode: AmountMode
  /** signed mode: a single amount column + sign convention. */
  amountIndex: number
  expenseIsNegative: boolean
  /** debitCredit mode: money-out and money-in columns. */
  debitIndex: number
  creditIndex: number
}

/** Remembered per-account import mapping. */
export interface ImportProfile extends ImportMapping {
  accountId: string
}

/** One CSV import, used to group + undo its transactions. */
export interface ImportBatch {
  id: string
  accountId: string
  fileName: string
  /** ISO timestamp. */
  createdAt: string
  count: number
}
