import { describe, it, expect } from 'vitest'
import { matchCategory } from './rules'
import type { MatchableRule } from './rules'

const rules: MatchableRule[] = [
  { pattern: 'biedronka', categoryId: 'groceries', priority: 1 },
  { pattern: 'orlen', categoryId: 'fuel', priority: 1 },
  { pattern: 'orlen station', categoryId: 'travel', priority: 5 },
  { pattern: 'shop', categoryId: 'shopping', priority: 0 },
]

describe('matchCategory', () => {
  it('matches case-insensitively as a substring', () => {
    expect(matchCategory('Płatność BIEDRONKA 4012', rules)).toBe('groceries')
  })

  it('prefers the higher-priority rule on overlap', () => {
    // contains both "orlen" (pri 1) and "orlen station" (pri 5)
    expect(matchCategory('ORLEN STATION 22', rules)).toBe('travel')
  })

  it('returns null when nothing matches', () => {
    expect(matchCategory('Unknown vendor', rules)).toBeNull()
  })

  it('ignores empty patterns', () => {
    expect(matchCategory('anything', [{ pattern: '  ', categoryId: 'x', priority: 9 }])).toBeNull()
  })
})
