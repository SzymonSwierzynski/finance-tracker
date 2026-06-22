import { useLiveQuery } from 'dexie-react-hooks'
import { listAccounts } from './accounts'
import { listCategories } from './categories'
import { listTransactions, getMonthSummary, getCategoryBreakdown } from './transactions'
import { readSettings } from './settings'
import { listRules } from './rules'
import { listImportBatches } from './import'
import type { TransactionFilter, MonthSummary } from './transactions'
import type { CategoryBreakdown } from './breakdown'
import type { DateRange } from '@/lib/period'
import type { Account, Category, CategoryKind, Transaction, Settings, Rule, ImportBatch } from './types'

/**
 * Reactive read hooks. These wrap Dexie's useLiveQuery so components stay free
 * of any direct Dexie awareness — the queries re-run automatically whenever the
 * underlying tables change. `undefined` means "still loading".
 */

export function useAccounts(includeArchived = false): Account[] | undefined {
  return useLiveQuery(() => listAccounts(includeArchived), [includeArchived])
}

export function useCategories(): Category[] | undefined {
  return useLiveQuery(() => listCategories(), [])
}

export function useTransactions(filter: TransactionFilter = {}): Transaction[] | undefined {
  const { month, start, end, accountId, categoryId, type } = filter
  return useLiveQuery(
    () => listTransactions({ month, start, end, accountId, categoryId, type }),
    [month, start, end, accountId, categoryId, type],
  )
}

export function useMonthSummary(month: string): MonthSummary | undefined {
  return useLiveQuery(() => getMonthSummary(month), [month])
}

export function useCategoryBreakdown(range: DateRange, kind: CategoryKind): CategoryBreakdown | undefined {
  return useLiveQuery(() => getCategoryBreakdown(range, kind), [range.start, range.end, kind])
}

export function useSettings(): Settings | undefined {
  return useLiveQuery(() => readSettings(), [])
}

export function useRules(): Rule[] | undefined {
  return useLiveQuery(() => listRules(), [])
}

export function useImportBatches(): ImportBatch[] | undefined {
  return useLiveQuery(() => listImportBatches(), [])
}
