import { describe, it, expect } from 'vitest'
import { parseFlexibleDate } from './csvDate'

describe('parseFlexibleDate', () => {
  it('auto-detects common Polish formats', () => {
    expect(parseFlexibleDate('2026-05-15', 'auto')).toBe('2026-05-15')
    expect(parseFlexibleDate('15.05.2026', 'auto')).toBe('2026-05-15')
    expect(parseFlexibleDate('15-05-2026', 'auto')).toBe('2026-05-15')
  })

  it('uses an explicit format when given', () => {
    expect(parseFlexibleDate('15/05/2026', 'dd/MM/yyyy')).toBe('2026-05-15')
  })

  it('rejects empties and garbage', () => {
    expect(parseFlexibleDate('', 'auto')).toBeNull()
    expect(parseFlexibleDate('   ', 'auto')).toBeNull()
    expect(parseFlexibleDate('not a date', 'auto')).toBeNull()
  })

  it('rejects out-of-range dates via the round-trip check', () => {
    expect(parseFlexibleDate('2026-13-40', 'auto')).toBeNull()
    expect(parseFlexibleDate('32.01.2026', 'dd.MM.yyyy')).toBeNull()
  })
})
