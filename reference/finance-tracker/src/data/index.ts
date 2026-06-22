/**
 * Public data-layer API. Components import everything data-related from "@/data"
 * and nothing else — the Dexie `db` instance is intentionally NOT re-exported.
 */

export type {
  Account,
  AccountType,
  Category,
  CategoryKind,
  Transaction,
  TransactionType,
  Rule,
  Settings,
  AmountMode,
  ImportMapping,
  ImportProfile,
  ImportBatch,
} from './types'

export { createAccount, listAccounts, getAccount, updateAccount, archiveAccount } from './accounts'
export type { CreateAccountInput } from './accounts'

export { createCategory, listCategories, getCategory } from './categories'
export type { CreateCategoryInput } from './categories'

export {
  createTransaction,
  listTransactions,
  getTransaction,
  deleteTransaction,
  getMonthSummary,
  getCategoryBreakdown,
} from './transactions'
export type { CreateTransactionInput, TransactionFilter, MonthSummary } from './transactions'

export { computeBreakdown, UNCATEGORIZED_COLOR } from './breakdown'
export type { CategoryBreakdown, BreakdownParent, BreakdownChild } from './breakdown'

export { createRule, listRules, updateRule, deleteRule } from './rules'
export type { CreateRuleInput } from './rules'

export { buildImportRows } from './importRows'
export type { ParsedImportRow } from './importRows'

export {
  commitImport,
  listImportBatches,
  undoImportBatch,
  getImportProfile,
  saveImportProfile,
} from './import'
export type { CommitImportInput, CommitImportResult } from './import'

export {
  getSettings,
  updateSettings,
  setRate,
  getRateToBase,
  resolveRateToBase,
  SETTINGS_ID,
  DEFAULT_REPORTING_CURRENCY,
} from './settings'

export { ensureSeeded, seedDefaultCategories } from './seed'

export {
  useAccounts,
  useCategories,
  useTransactions,
  useMonthSummary,
  useCategoryBreakdown,
  useSettings,
  useRules,
  useImportBatches,
} from './hooks'
