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
export type CategoryKind = 'expense' | 'income'

// Request contracts (generated).
export type RegisterRequest = S['RegisterRequest']
export type LoginRequest = S['LoginRequest']
export type CreateAccountRequest = S['CreateAccountRequest']
export type UpdateAccountRequest = S['UpdateAccountRequest']
export type CreateTransactionRequest = S['CreateTransactionRequest']
export type UpdateTransactionRequest = S['UpdateTransactionRequest']
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

export const ACCOUNT_TYPES: AccountType[] = ['checking', 'savings', 'cash', 'credit']
export const TRANSACTION_TYPES: TransactionType[] = ['expense', 'income', 'transfer']
