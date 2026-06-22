import Dexie, { type Table } from 'dexie'
import type {
  Account,
  Category,
  Transaction,
  Rule,
  Settings,
  ImportBatch,
  ImportProfile,
} from './types'

/**
 * The IndexedDB database. This Dexie instance is PRIVATE to the data layer —
 * nothing outside src/data may import it (enforced by an ESLint rule). All
 * reads/writes go through the typed functions in the sibling modules so a
 * future swap to a real backend API stays contained.
 */
export class FinanceDB extends Dexie {
  // `declare` keeps these type-only so Dexie's own table accessors are not
  // overwritten by class-field initializers under `useDefineForClassFields`.
  declare accounts: Table<Account, string>
  declare categories: Table<Category, string>
  declare transactions: Table<Transaction, string>
  declare rules: Table<Rule, string>
  declare settings: Table<Settings, string>
  declare importBatches: Table<ImportBatch, string>
  declare importProfiles: Table<ImportProfile, string>

  constructor() {
    super('finance-tracker')
    // Schema v1. Primary keys are app-generated UUID strings (portable to a
    // future backend). Listed fields after the PK are secondary indexes.
    this.version(1).stores({
      accounts: 'id, name, archived',
      categories: 'id, parentId, kind',
      transactions: 'id, date, accountId, categoryId, type, importBatchId, dedupeHash',
      rules: 'id, priority',
      settings: 'id',
    })
    // v2 (additive): CSV import support. Unlisted stores carry over unchanged.
    this.version(2).stores({
      importBatches: 'id, accountId, createdAt',
      importProfiles: 'accountId',
    })
  }
}

export const db = new FinanceDB()
