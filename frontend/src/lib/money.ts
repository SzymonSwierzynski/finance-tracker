/**
 * Money utilities — INTEGER MINOR UNITS ONLY (mirrors the backend MoneyUtil and the prototype's
 * lib/money.ts). 19.99 PLN is 1999. We never do float arithmetic on amounts; the only division by
 * 100 is in the display formatter below.
 */

export const MINOR_UNIT_EXPONENT = 2
const MINOR_UNIT_FACTOR = 10 ** MINOR_UNIT_EXPONENT // 100

/**
 * Parse a human-typed amount into integer minor units. Tolerant of Polish formats: dot/comma
 * decimals, space/NBSP/EU/US thousands separators, optional leading sign. Fractions longer than the
 * exponent round half-up. Returns null for anything unparseable. (Ported verbatim from the prototype.)
 */
export function parseAmountToMinor(input: string): number | null {
  if (typeof input !== 'string') return null
  let s = input.trim()
  if (s === '') return null

  let negative = false
  if (s.startsWith('-')) {
    negative = true
    s = s.slice(1)
  } else if (s.startsWith('+')) {
    s = s.slice(1)
  }

  s = s.replace(/\s/g, '') // JS \s covers NBSP / narrow NBSP / thin space
  if (s === '') return null
  if (!/^[0-9.,]+$/.test(s)) return null

  const commaCount = (s.match(/,/g) ?? []).length
  const dotCount = (s.match(/\./g) ?? []).length

  let decimalSep: '.' | ',' | null
  if (commaCount > 0 && dotCount > 0) {
    decimalSep = s.lastIndexOf(',') > s.lastIndexOf('.') ? ',' : '.'
  } else if (commaCount === 1) {
    decimalSep = ','
  } else if (dotCount === 1) {
    decimalSep = '.'
  } else {
    decimalSep = null
  }

  let intPart: string
  let fracPart: string
  if (decimalSep === null) {
    intPart = s.replace(/[.,]/g, '')
    fracPart = ''
  } else {
    const otherSep = decimalSep === ',' ? '.' : ','
    const cleaned = s.split(otherSep).join('')
    const idx = cleaned.lastIndexOf(decimalSep)
    intPart = cleaned.slice(0, idx).split(decimalSep).join('')
    fracPart = cleaned.slice(idx + 1)
  }

  if (intPart === '' && fracPart === '') return null
  if (!/^[0-9]*$/.test(intPart) || !/^[0-9]*$/.test(fracPart)) return null

  const intValue = intPart === '' ? 0 : Number(intPart)
  const fracHead = fracPart.slice(0, MINOR_UNIT_EXPONENT).padEnd(MINOR_UNIT_EXPONENT, '0')
  const fracValue = Number(fracHead)
  const roundUp =
    fracPart.length > MINOR_UNIT_EXPONENT && Number(fracPart[MINOR_UNIT_EXPONENT]) >= 5

  const minor = intValue * MINOR_UNIT_FACTOR + fracValue + (roundUp ? 1 : 0)
  if (!Number.isFinite(minor)) return null
  return negative ? -minor : minor
}

export interface FormatMoneyOptions {
  locale?: string
  currencyDisplay?: 'symbol' | 'code' | 'none'
  signDisplay?: 'auto' | 'always' | 'exceptZero' | 'never'
}

/**
 * Format integer minor units for display. The ONLY place we divide by 100, feeding Intl purely for
 * rendering — never for storage.
 */
export function formatMinor(
  minor: number,
  currency = 'PLN',
  opts: FormatMoneyOptions = {},
): string {
  const { locale = 'pl-PL', currencyDisplay = 'symbol', signDisplay = 'auto' } = opts
  const value = minor / MINOR_UNIT_FACTOR

  if (currencyDisplay === 'none') {
    return new Intl.NumberFormat(locale, {
      style: 'decimal',
      minimumFractionDigits: MINOR_UNIT_EXPONENT,
      maximumFractionDigits: MINOR_UNIT_EXPONENT,
      useGrouping: true,
      signDisplay,
    }).format(value)
  }

  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    currencyDisplay: currencyDisplay === 'code' ? 'code' : 'symbol',
    useGrouping: true,
    signDisplay,
  }).format(value)
}

/** Major-unit number for charts (never use for storage/precision-critical math). */
export function toMajorNumber(minor: number): number {
  return minor / MINOR_UNIT_FACTOR
}
