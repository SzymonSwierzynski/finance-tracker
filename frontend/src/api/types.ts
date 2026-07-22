import type { components } from './types.gen'

/**
 * App-facing types. Enums and *request* bodies are taken straight from the generated OpenAPI
 * schema (the single source of truth). Response view-models are declared explicitly as
 * fully-present: the backend always populates these fields, but OpenAPI marks record components
 * optional, which is awkward to consume under strict null checks.
 */
type S = components['schemas']

export type AccountType = NonNullable<S['CreateAccountRequest']['type']>
export type TransactionType = NonNullable<S['CreateTransactionRequest']['type']>
export type CategoryKind = NonNullable<S['CreateCategoryRequest']['kind']>

// Request contracts (generated).
export type RegisterRequest = S['RegisterRequest']
export type LoginRequest = S['LoginRequest']
export type CreateAccountRequest = S['CreateAccountRequest']
export type UpdateAccountRequest = S['UpdateAccountRequest']
export type CreateTransactionRequest = S['CreateTransactionRequest']
export type UpdateTransactionRequest = S['UpdateTransactionRequest']
export type CreateCategoryRequest = S['CreateCategoryRequest']
export type UpdateCategoryRequest = S['UpdateCategoryRequest']
export type UpdateSettingsRequest = S['UpdateSettingsRequest']

// Response view-models.
export interface UserProfile {
  id: number
  email: string
  displayName: string | null
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: UserProfile
}

export interface Account {
  id: number
  name: string
  type: AccountType
  currency: string
  startingBalanceMinor: number | null
  trackBalance: boolean
  archived: boolean
  version: number
}

export interface AccountBalance {
  accountId: number
  currency: string
  balanceMinor: number
}

export interface Transaction {
  id: number
  date: string
  amountMinor: number
  type: TransactionType
  accountId: number
  counterAccountId: number | null
  categoryId: number | null
  currency: string
  rateToBase: number
  baseMinor: number
  description: string
  note: string
  importBatchId: number | null
  dedupeHash: string
  version: number
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface Summary {
  from: string
  to: string
  currency: string
  incomeMinor: number
  expenseMinor: number
  netMinor: number
}

export interface Settings {
  reportingCurrency: string
}

/**
 * One user-maintained exchange rate into the reporting currency. `stale` means the rate was saved
 * against a different base than the one in force now, so the backend will refuse to use it — the
 * value is kept only so the user can see what to re-enter.
 *
 * `rateToBase` is a number, not minor units: rates are the one non-integer quantity in the model.
 */
export interface FxRate {
  currency: string
  rateToBase: number
  baseCurrency: string
  stale: boolean
  source: string | null
  lastFetchedAt: string | null
  version: number
}

export interface FxRates {
  baseCurrency: string
  rates: FxRate[]
}

/** Declared by hand until the next `npm run gen:api` picks it up from the OpenAPI spec. */
export interface UpsertFxRateRequest {
  rateToBase: number
  source?: string
}

export interface Category {
  id: number
  name: string
  kind: CategoryKind
  parentId: number | null
  color: string
  version: number
}

export interface DeleteCategoryResult {
  uncategorizedTransactions: number
}

export interface BreakdownChild {
  categoryId: number | null
  name: string
  baseMinor: number
  share: number
}

export interface BreakdownParent {
  categoryId: number | null
  name: string
  color: string
  baseMinor: number
  share: number
  children: BreakdownChild[]
}

export interface Breakdown {
  kind: CategoryKind
  from: string
  to: string
  currency: string
  totalBaseMinor: number
  count: number
  parents: BreakdownParent[]
}

export const ACCOUNT_TYPES: AccountType[] = ['checking', 'savings', 'cash', 'credit']
export const TRANSACTION_TYPES: TransactionType[] = ['expense', 'income', 'transfer']
export const CATEGORY_KINDS: CategoryKind[] = ['expense', 'income']

/** Fixed Uncategorized slice colour (matches the backend). */
export const UNCATEGORIZED_COLOR = '#94a3b8'
