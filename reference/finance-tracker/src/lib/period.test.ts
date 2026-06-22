import { describe, it, expect } from 'vitest'
import { periodBounds, periodLabel, shiftPeriod } from './period'

describe('periodBounds', () => {
  it('month', () => {
    expect(periodBounds({ type: 'month', anchor: '2026-05' })).toEqual({
      start: '2026-05-01',
      end: '2026-05-31',
    })
  })
  it('handles leap February', () => {
    expect(periodBounds({ type: 'month', anchor: '2024-02' })).toEqual({
      start: '2024-02-01',
      end: '2024-02-29',
    })
  })
  it('year', () => {
    expect(periodBounds({ type: 'year', anchor: '2026' })).toEqual({
      start: '2026-01-01',
      end: '2026-12-31',
    })
  })
  it('custom passes through', () => {
    expect(periodBounds({ type: 'custom', start: '2026-01-15', end: '2026-03-10' })).toEqual({
      start: '2026-01-15',
      end: '2026-03-10',
    })
  })
})

describe('shiftPeriod', () => {
  it('steps months across a year boundary', () => {
    expect(shiftPeriod({ type: 'month', anchor: '2026-12' }, 1)).toEqual({
      type: 'month',
      anchor: '2027-01',
    })
  })
  it('steps years', () => {
    expect(shiftPeriod({ type: 'year', anchor: '2026' }, -1)).toEqual({ type: 'year', anchor: '2025' })
  })
  it('is a no-op for all', () => {
    expect(shiftPeriod({ type: 'all' }, 1)).toEqual({ type: 'all' })
  })
})

describe('periodLabel', () => {
  it('labels all time', () => {
    expect(periodLabel({ type: 'all' })).toBe('All time')
  })
  it('labels a year', () => {
    expect(periodLabel({ type: 'year', anchor: '2026' })).toBe('2026')
  })
})
