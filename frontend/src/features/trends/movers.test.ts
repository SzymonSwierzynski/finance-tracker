import { describe, expect, it } from 'vitest'
import { moverPercent, moverState } from './movers'

describe('moverState', () => {
  it('is "new" when there was no previous spend', () => {
    expect(moverState({ currentMinor: 300, previousMinor: 0, deltaMinor: 300 })).toBe('new')
  })
  it('is "gone" when there is no current spend', () => {
    expect(moverState({ currentMinor: 0, previousMinor: 800, deltaMinor: -800 })).toBe('gone')
  })
  it('is "up" when spending rose', () => {
    expect(moverState({ currentMinor: 3000, previousMinor: 2000, deltaMinor: 1000 })).toBe('up')
  })
  it('is "down" when spending fell', () => {
    expect(moverState({ currentMinor: 2000, previousMinor: 2500, deltaMinor: -500 })).toBe('down')
  })
  it('is "flat" when unchanged', () => {
    expect(moverState({ currentMinor: 500, previousMinor: 500, deltaMinor: 0 })).toBe('flat')
  })
})

describe('moverPercent', () => {
  it('returns the signed percentage vs the previous value', () => {
    expect(moverPercent({ deltaMinor: 1000, previousMinor: 2000 })).toBe(50)
  })
  it('returns null when there is no previous base', () => {
    expect(moverPercent({ deltaMinor: 300, previousMinor: 0 })).toBeNull()
  })
})
