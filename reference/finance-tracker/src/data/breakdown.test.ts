import { describe, it, expect } from 'vitest'
import { computeBreakdown } from './breakdown'
import type { Category, Transaction } from './types'

let seq = 0
function tx(partial: Partial<Transaction>): Transaction {
  return {
    id: `t${seq++}`,
    date: '2026-05-10',
    amountMinor: 0,
    type: 'expense',
    accountId: 'a',
    counterAccountId: null,
    categoryId: null,
    currency: 'PLN',
    rateToBase: 1,
    description: '',
    note: '',
    importBatchId: null,
    dedupeHash: '',
    ...partial,
  }
}

const categories: Category[] = [
  { id: 'g', name: 'Groceries', kind: 'expense', parentId: null, color: '#22c55e' },
  { id: 'g1', name: 'Supermarket', kind: 'expense', parentId: 'g', color: '#22c55e' },
  { id: 'g2', name: 'Convenience', kind: 'expense', parentId: 'g', color: '#22c55e' },
  { id: 't', name: 'Transport', kind: 'expense', parentId: null, color: '#3b82f6' },
  { id: 'sal', name: 'Salary', kind: 'income', parentId: null, color: '#10b981' },
]

describe('computeBreakdown — expenses', () => {
  const txs = [
    tx({ amountMinor: 1000, categoryId: 'g1' }), // Groceries > Supermarket
    tx({ amountMinor: 2000, categoryId: 'g2' }), // Groceries > Convenience
    tx({ amountMinor: 500, categoryId: 'g' }), // Groceries (direct)
    tx({ amountMinor: 3000, categoryId: 't' }), // Transport (direct only)
    tx({ amountMinor: 700, categoryId: null }), // Uncategorized
    tx({ amountMinor: 9999, type: 'income', categoryId: 'sal' }), // excluded
  ]
  const b = computeBreakdown(txs, categories, 'expense')

  it('totals only expenses', () => {
    expect(b.totalBaseMinor).toBe(7200)
    expect(b.count).toBe(5)
  })

  it('rolls up parents, sorted desc', () => {
    expect(b.parents.map((p) => [p.name, p.baseMinor])).toEqual([
      ['Groceries', 3500],
      ['Transport', 3000],
      ['Uncategorized', 700],
    ])
  })

  it('splits Groceries into children incl. a direct slice', () => {
    const g = b.parents[0]
    expect(g.children.map((c) => [c.name, c.baseMinor])).toEqual([
      ['Convenience', 2000],
      ['Supermarket', 1000],
      ['Groceries (direct)', 500],
    ])
    // the direct slice maps back to the parent's own id
    expect(g.children.find((c) => c.name === 'Groceries (direct)')?.categoryId).toBe('g')
  })

  it('leaves direct-only and uncategorized parents childless', () => {
    expect(b.parents[1].children).toEqual([]) // Transport
    expect(b.parents[2].categoryId).toBeNull() // Uncategorized
    expect(b.parents[2].children).toEqual([])
  })

  it('computes shares', () => {
    expect(b.parents[0].share).toBeCloseTo(3500 / 7200, 6)
  })
})

describe('computeBreakdown — income + currency conversion', () => {
  it('aggregates the income side', () => {
    const b = computeBreakdown(
      [tx({ amountMinor: 9999, type: 'income', categoryId: 'sal' })],
      categories,
      'income',
    )
    expect(b.totalBaseMinor).toBe(9999)
    expect(b.parents[0].name).toBe('Salary')
  })

  it('converts native amounts to base via rateToBase', () => {
    const b = computeBreakdown(
      [tx({ amountMinor: 1000, currency: 'EUR', rateToBase: 4.3, categoryId: 't' })],
      categories,
      'expense',
    )
    expect(b.totalBaseMinor).toBe(4300) // round(1000 * 4.3)
    expect(b.parents[0].name).toBe('Transport')
  })
})
