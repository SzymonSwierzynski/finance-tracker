import { db } from './db'
import { getSettings } from './settings'
import { createAccount } from './accounts'
import { createCategory } from './categories'
import type { CategoryKind } from './types'

interface SeedParent {
  name: string
  kind: CategoryKind
  color: string
  children: string[]
}

const DEFAULT_CATEGORIES: SeedParent[] = [
  { name: 'Groceries', kind: 'expense', color: '#22c55e', children: ['Supermarket', 'Convenience'] },
  { name: 'Eating out', kind: 'expense', color: '#f97316', children: ['Restaurants', 'Coffee', 'Takeaway'] },
  { name: 'Transport', kind: 'expense', color: '#3b82f6', children: ['Fuel', 'Public transport', 'Taxi'] },
  { name: 'Housing', kind: 'expense', color: '#8b5cf6', children: ['Rent', 'Utilities', 'Internet'] },
  { name: 'Health', kind: 'expense', color: '#ef4444', children: ['Pharmacy', 'Doctor'] },
  { name: 'Entertainment', kind: 'expense', color: '#ec4899', children: ['Subscriptions', 'Hobbies'] },
  { name: 'Shopping', kind: 'expense', color: '#eab308', children: ['Clothes', 'Electronics'] },
  { name: 'Salary', kind: 'income', color: '#10b981', children: [] },
  { name: 'Other income', kind: 'income', color: '#14b8a6', children: [] },
]

export async function seedDefaultCategories(): Promise<void> {
  for (const parent of DEFAULT_CATEGORIES) {
    const created = await createCategory({ name: parent.name, kind: parent.kind, color: parent.color })
    for (const child of parent.children) {
      await createCategory({
        name: child,
        kind: parent.kind,
        parentId: created.id,
        color: parent.color,
      })
    }
  }
}

/**
 * Idempotent first-run setup, safe to call on every app start. Ensures Settings
 * exists; on a brand-new DB also seeds default categories and one starter
 * account so the core loop is usable immediately.
 *
 * Memoized per session: StrictMode mounts effects twice, and the two async runs
 * could otherwise both observe an empty DB and double-seed.
 */
let ensureSeededPromise: Promise<void> | null = null

export function ensureSeeded(): Promise<void> {
  if (!ensureSeededPromise) {
    ensureSeededPromise = runSeed()
  }
  return ensureSeededPromise
}

async function runSeed(): Promise<void> {
  await getSettings() // creates default settings if missing

  if ((await db.categories.count()) === 0) {
    await seedDefaultCategories()
  }

  if ((await db.accounts.count()) === 0) {
    await createAccount({ name: 'Cash', type: 'cash', currency: 'PLN', trackBalance: false })
  }
}
