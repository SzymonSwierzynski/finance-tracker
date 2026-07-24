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

export type ComparisonMode = 'month' | 'year'

export interface ComparisonPeriod {
  from: string
  to: string
  incomeMinor: number
  expenseMinor: number
  netMinor: number
}

/** Period-over-period comparison; `delta` is current − previous (base minor units). */
export interface Comparison {
  currency: string
  mode: ComparisonMode
  current: ComparisonPeriod
  previous: ComparisonPeriod
  delta: { incomeMinor: number; expenseMinor: number; netMinor: number }
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

// --- Phase 4: rules + CSV import ---

export type AmountMode = NonNullable<S['ImportMapping']['amountMode']>
export type CreateRuleRequest = S['CreateRuleRequest']
export type UpdateRuleRequest = S['UpdateRuleRequest']

export interface Rule {
  id: number
  pattern: string
  categoryId: number
  priority: number
  version: number
}

export interface ApplyRulesResult {
  scanned: number
  categorized: number
}

/** How to interpret a CSV against its columns (mirrors the backend ImportMapping). */
export interface ImportMapping {
  delimiter: string
  encoding: string
  hasHeader: boolean
  dateIndex: number
  dateFormat: string
  descriptionIndex: number
  amountMode: AmountMode
  amountIndex: number
  expenseIsNegative: boolean
  debitIndex: number
  creditIndex: number
}

export interface PreviewRow {
  index: number
  date: string | null
  amountMinor: number | null
  type: TransactionType
  description: string
  valid: boolean
  error: string | null
  duplicate: boolean
}

export interface PreviewResponse {
  delimiter: string
  misdecoded: boolean
  totalRows: number
  validRows: number
  duplicateRows: number
  rows: PreviewRow[]
}

export interface CommitResult {
  batchId: number | null
  imported: number
  skippedDuplicates: number
  skippedInvalid: number
}

export interface ImportBatch {
  id: number
  accountId: number
  fileName: string
  count: number
  createdAt: string
}

export const AMOUNT_MODES: AmountMode[] = ['signed', 'debitCredit']
export const SUPPORTED_ENCODINGS = ['utf-8', 'windows-1250', 'iso-8859-2'] as const
export const DATE_FORMAT_OPTIONS = [
  'auto',
  'yyyy-MM-dd',
  'dd.MM.yyyy',
  'dd-MM-yyyy',
  'dd/MM/yyyy',
  'yyyy/MM/dd',
] as const

// --- Phase 5: trend + cashflow ---

export type TrendInterval = 'month' | 'week'

export interface TrendBucket {
  period: string
  incomeMinor: number
  expenseMinor: number
}

export interface TrendResponse {
  from: string
  to: string
  interval: string
  currency: string
  buckets: TrendBucket[]
}

export interface CashflowBucket {
  period: string
  incomeMinor: number
  expenseMinor: number
  netMinor: number
  runningNetMinor: number
}

export interface CashflowResponse {
  from: string
  to: string
  interval: string
  currency: string
  buckets: CashflowBucket[]
}

export interface CategorySeries {
  categoryId: number | null
  name: string
  color: string
}

export interface CategoryTrendBucket {
  period: string
  /** series key (category id as text, or "uncategorized") -> base minor units */
  amounts: Record<string, number>
}

export interface CategoryTrendResponse {
  from: string
  to: string
  interval: string
  currency: string
  kind: CategoryKind
  series: CategorySeries[]
  buckets: CategoryTrendBucket[]
}

// --- Phase 6: recurring transactions ---

export type RecurringFrequency = 'daily' | 'weekly' | 'monthly' | 'yearly'

export interface Recurring {
  id: number
  accountId: number
  categoryId: number | null
  amountMinor: number
  type: TransactionType
  currency: string
  description: string
  note: string
  frequency: RecurringFrequency
  intervalCount: number
  startDate: string
  endDate: string | null
  nextRunDate: string
  active: boolean
  version: number
}

export interface CreateRecurringRequest {
  accountId: number
  amountMinor: number
  type: TransactionType
  categoryId?: number
  currency?: string
  description?: string
  note?: string
  frequency: RecurringFrequency
  intervalCount?: number
  startDate: string
  endDate?: string
}

export interface UpdateRecurringRequest {
  version: number
  amountMinor?: number
  description?: string
  note?: string
  active?: boolean
  endDate?: string
}

export const RECURRING_FREQUENCIES: RecurringFrequency[] = ['daily', 'weekly', 'monthly', 'yearly']

export const ACCOUNT_TYPES: AccountType[] = ['checking', 'savings', 'cash', 'credit']
export const TRANSACTION_TYPES: TransactionType[] = ['expense', 'income', 'transfer']
export const CATEGORY_KINDS: CategoryKind[] = ['expense', 'income']

/** Fixed Uncategorized slice colour (matches the backend). */
export const UNCATEGORIZED_COLOR = '#94a3b8'

// --- Phase 7: budgets ---
// Request bodies are hand-declared here as an interim; run `npm run gen:api` (backend running) to
// source them from the OpenAPI schema like the other request contracts above.

export interface CreateBudgetRequest {
  categoryId: number
  amountMinor: number
}

export interface UpdateBudgetRequest {
  amountMinor: number
  version: number
}

export interface BudgetResponse {
  id: number
  categoryId: number
  amountMinor: number
  version: number
}

/** One budget's progress for a month, in base-currency minor units. */
export interface BudgetProgress {
  id: number
  categoryId: number
  categoryName: string
  color: string
  amountMinor: number
  spentMinor: number
  remainingMinor: number
  over: boolean
  version: number
}

export interface Budgets {
  month: string
  currency: string
  items: BudgetProgress[]
}
