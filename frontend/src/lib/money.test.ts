import { describe, expect, it } from 'vitest'
import { formatMinor, parseAmountToMinor, toMajorNumber } from './money'

describe('parseAmountToMinor', () => {
  it('parses plain dot and comma decimals', () => {
    expect(parseAmountToMinor('19.99')).toBe(1999)
    expect(parseAmountToMinor('19,99')).toBe(1999)
  })

  it('parses space / NBSP thousands with a decimal comma (Polish)', () => {
    expect(parseAmountToMinor('1 234,56')).toBe(123456)
    expect(parseAmountToMinor('1\u00a0234,56')).toBe(123456) // NBSP
    expect(parseAmountToMinor('1\u202f234,56')).toBe(123456) // narrow NBSP
  })

  it('parses EU and US grouped formats (last separator wins)', () => {
    expect(parseAmountToMinor('1.234,56')).toBe(123456) // EU
    expect(parseAmountToMinor('1,234.56')).toBe(123456) // US
  })

  it('treats a lone comma as a decimal (Polish convention)', () => {
    expect(parseAmountToMinor('1,5')).toBe(150)
  })

  it('treats repeated separators as thousands groupings', () => {
    expect(parseAmountToMinor('1.234.567')).toBe(123456700)
  })

  it('rounds half-up beyond two decimals', () => {
    expect(parseAmountToMinor('1.005')).toBe(101)
    expect(parseAmountToMinor('1.004')).toBe(100)
  })

  it('handles signs and whole numbers', () => {
    expect(parseAmountToMinor('-12,50')).toBe(-1250)
    expect(parseAmountToMinor('1234')).toBe(123400)
  })

  it('returns null for non-numeric input', () => {
    expect(parseAmountToMinor('')).toBeNull()
    expect(parseAmountToMinor('abc')).toBeNull()
    expect(parseAmountToMinor('   ')).toBeNull()
  })
})

describe('formatMinor', () => {
  it('formats integer minor units, dividing by 100 only here', () => {
    // pl-PL uses a comma decimal and a non-breaking-space grouping; assert on the digits.
    const formatted = formatMinor(123456, 'PLN', { locale: 'pl-PL' })
    expect(formatted).toContain('1')
    expect(formatted).toContain('234,56')
  })

  it('respects currencyDisplay none for plain numbers', () => {
    expect(formatMinor(199900, 'PLN', { locale: 'en-GB', currencyDisplay: 'none' })).toBe('1,999.00')
  })
})

describe('toMajorNumber', () => {
  it('converts minor to a major-unit number', () => {
    expect(toMajorNumber(1999)).toBe(19.99)
  })
})
