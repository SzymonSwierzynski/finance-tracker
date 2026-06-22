import { toBaseMinor } from '@/lib/money'
import type { Category, CategoryKind, Transaction } from './types'

/** Slate-400 — the bucket colour for uncategorized spend. */
export const UNCATEGORIZED_COLOR = '#94a3b8'

export interface BreakdownChild {
  /** A real subcategory id, OR the parent's own id for its "(direct)" slice. */
  categoryId: string
  name: string
  baseMinor: number
  /** Fraction of the parent total, 0..1. */
  share: number
}

export interface BreakdownParent {
  /** null = the Uncategorized bucket. */
  categoryId: string | null
  name: string
  color: string
  baseMinor: number
  /** Fraction of the grand total, 0..1. */
  share: number
  /** Subcategory splits (empty for a parent with no sub-spend). */
  children: BreakdownChild[]
}

export interface CategoryBreakdown {
  kind: CategoryKind
  totalBaseMinor: number
  count: number
  /** Parents sorted by amount, descending. */
  parents: BreakdownParent[]
}

interface ParentAcc {
  categoryId: string | null
  name: string
  color: string
  direct: number
  base: number
  children: Map<string, { name: string; base: number }>
}

const UNCAT_KEY = '__uncategorized__'

/**
 * Roll transactions up into a two-level category breakdown, all in base
 * (reporting) minor units. Only transactions whose `type` matches `kind`
 * (expense or income) are counted; transfers are ignored. Spend booked
 * directly on a parent that also has subcategory spend becomes a synthetic
 * "(direct)" child so the parent's drill-down still sums to its total.
 */
export function computeBreakdown(
  transactions: Transaction[],
  categories: Category[],
  kind: CategoryKind,
): CategoryBreakdown {
  const byId = new Map(categories.map((c) => [c.id, c]))
  const parents = new Map<string, ParentAcc>()
  let total = 0
  let count = 0

  const ensure = (key: string, categoryId: string | null, name: string, color: string): ParentAcc => {
    let acc = parents.get(key)
    if (!acc) {
      acc = { categoryId, name, color, direct: 0, base: 0, children: new Map() }
      parents.set(key, acc)
    }
    return acc
  }

  for (const t of transactions) {
    if (t.type !== kind) continue
    const base = toBaseMinor(t.amountMinor, t.rateToBase)
    total += base
    count++

    const cat = t.categoryId ? byId.get(t.categoryId) : undefined
    if (!cat) {
      const acc = ensure(UNCAT_KEY, null, 'Uncategorized', UNCATEGORIZED_COLOR)
      acc.direct += base
      acc.base += base
    } else if (cat.parentId == null) {
      const acc = ensure(cat.id, cat.id, cat.name, cat.color)
      acc.direct += base
      acc.base += base
    } else {
      const parent = byId.get(cat.parentId)
      const acc = ensure(
        cat.parentId,
        parent?.id ?? cat.parentId,
        parent?.name ?? 'Unknown',
        parent?.color ?? UNCATEGORIZED_COLOR,
      )
      const child = acc.children.get(cat.id) ?? { name: cat.name, base: 0 }
      child.base += base
      acc.children.set(cat.id, child)
      acc.base += base
    }
  }

  const outParents: BreakdownParent[] = []
  for (const [key, acc] of parents) {
    const children: BreakdownChild[] = []
    for (const [childId, c] of acc.children) {
      children.push({ categoryId: childId, name: c.name, baseMinor: c.base, share: acc.base ? c.base / acc.base : 0 })
    }
    // Surface the parent's own direct spend as a slice when it has subcategories too.
    if (key !== UNCAT_KEY && acc.direct > 0 && acc.children.size > 0) {
      children.push({
        categoryId: acc.categoryId as string,
        name: `${acc.name} (direct)`,
        baseMinor: acc.direct,
        share: acc.base ? acc.direct / acc.base : 0,
      })
    }
    children.sort((a, b) => b.baseMinor - a.baseMinor)
    outParents.push({
      categoryId: acc.categoryId,
      name: acc.name,
      color: acc.color,
      baseMinor: acc.base,
      share: total ? acc.base / total : 0,
      children,
    })
  }
  outParents.sort((a, b) => b.baseMinor - a.baseMinor)

  return { kind, totalBaseMinor: total, count, parents: outParents }
}
