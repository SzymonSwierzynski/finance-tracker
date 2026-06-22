import { describe, it, expect } from 'vitest'
import {
  parseAmountToMinor,
  formatMinor,
  toBaseMinor,
  sumMinor,
  isValidAmountInput,
} from './money'

describe('parseAmountToMinor', () => {
  it('parses plain dot decimals', () => {
    expect(parseAmountToMinor('19.99')).toBe(1999)
    expect(parseAmountToMinor('0.01')).toBe(1)
    expect(parseAmountToMinor('1234')).toBe(123400)
    expect(parseAmountToMinor('5.')).toBe(500)
    expect(parseAmountToMinor('.5')).toBe(50)
  })

  it('parses Polish decimal comma', () => {
    expect(parseAmountToMinor('19,99')).toBe(1999)
    expect(parseAmountToMinor('0,01')).toBe(1)
    expect(parseAmountToMinor('1,5')).toBe(150)
  })

  it('parses thousands separators (spaces / NBSP)', () => {
    expect(parseAmountToMinor('1 234,56')).toBe(123456)
    expect(parseAmountToMinor('1 234,56')).toBe(123456)
    expect(parseAmountToMinor('1 000 000')).toBe(100000000)
  })

  it('parses mixed thousands + decimal (EU and US)', () => {
    expect(parseAmountToMinor('1.234,56')).toBe(123456) // EU
    expect(parseAmountToMinor('1,234.56')).toBe(123456) // US
    expect(parseAmountToMinor('1.234.567')).toBe(123456700) // EU thousands only
    expect(parseAmountToMinor('1,234,567')).toBe(123456700) // US thousands only
  })

  it('rounds fractions beyond minor units half-up', () => {
    expect(parseAmountToMinor('0.005')).toBe(1)
    expect(parseAmountToMinor('1.004')).toBe(100)
    expect(parseAmountToMinor('9.999')).toBe(1000) // carry across the unit
  })

  it('handles optional signs', () => {
    expect(parseAmountToMinor('-12,50')).toBe(-1250)
    expect(parseAmountToMinor('+5')).toBe(500)
  })

  it('returns null for invalid input', () => {
    expect(parseAmountToMinor('')).toBeNull()
    expect(parseAmountToMinor('   ')).toBeNull()
    expect(parseAmountToMinor('abc')).toBeNull()
    expect(parseAmountToMinor('.')).toBeNull()
    expect(parseAmountToMinor('12zł')).toBeNull()
  })
})

describe('toBaseMinor', () => {
  it('rounds to the nearest base minor unit', () => {
    expect(toBaseMinor(1000, 1)).toBe(1000)
    expect(toBaseMinor(1000, 4.3567)).toBe(4357) // 4356.7 -> 4357
    expect(toBaseMinor(199, 0.23)).toBe(46) // 45.77 -> 46
  })
})

describe('sumMinor', () => {
  it('adds integers exactly', () => {
    expect(sumMinor([1999, 1, 5000])).toBe(7000)
    expect(sumMinor([])).toBe(0)
  })
})

describe('formatMinor', () => {
  it('formats PLN in pl-PL with two decimals and symbol', () => {
    const s = formatMinor(1999, 'PLN')
    expect(s).toMatch(/19,99/)
    expect(s).toMatch(/zł/)
  })

  it('can format without any currency marker', () => {
    // pl-PL groups thousands with a (narrow) space; match loosely.
    expect(formatMinor(123456, 'PLN', { currencyDisplay: 'none' })).toMatch(/1.234,56|1234,56/)
  })

  it('groups thousands even for 4-digit values', () => {
    // Normalise the (narrow) no-break separator to a regular space.
    const grouped = formatMinor(500000, 'PLN', { currencyDisplay: 'none' }).replace(/\s/g, ' ')
    expect(grouped).toContain('5 000')
  })

  it('round-trips parse -> format -> parse', () => {
    const minor = parseAmountToMinor('1 234,56')
    expect(minor).toBe(123456)
    const text = formatMinor(minor!, 'PLN', { currencyDisplay: 'none' })
    expect(parseAmountToMinor(text)).toBe(123456)
  })
})

describe('isValidAmountInput', () => {
  it('accepts positive amounts', () => {
    expect(isValidAmountInput('19,99')).toBe(true)
    expect(isValidAmountInput('0,01')).toBe(true)
  })

  it('rejects zero, empty and garbage', () => {
    expect(isValidAmountInput('0')).toBe(false)
    expect(isValidAmountInput('')).toBe(false)
    expect(isValidAmountInput('abc')).toBe(false)
  })
})
