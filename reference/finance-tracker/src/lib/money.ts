/**
 * Money utilities — INTEGER MINOR UNITS ONLY.
 *
 * Money is stored as integers in minor units (grosze / cents): 19.99 PLN -> 1999.
 * We never store or do arithmetic on floats. The only place a value is divided
 * by 100 is `formatMinor`, purely for display (per the project's money rule).
 *
 * Phase 1 assumes 2-decimal currencies (PLN, EUR, USD, GBP, ...). The exponent
 * is centralized here so a per-currency table (e.g. JPY = 0) can be introduced
 * later without changing any call sites.
 */

export const MINOR_UNIT_EXPONENT = 2
const MINOR_UNIT_FACTOR = 10 ** MINOR_UNIT_EXPONENT // 100

/**
 * Parse a human-typed amount into integer minor units.
 *
 * Tolerant of the formats a Polish user actually types:
 *  - "19.99"      -> 1999      (dot decimal)
 *  - "19,99"      -> 1999      (comma decimal)
 *  - "1 234,56"   -> 123456    (space/NBSP thousands + comma decimal)
 *  - "1.234,56"   -> 123456    (EU: dot thousands, comma decimal)
 *  - "1,234.56"   -> 123456    (US: comma thousands, dot decimal)
 *  - "1234"       -> 123400    (no fraction)
 *  - "-12,50"     -> -1250     (optional leading sign)
 *
 * Fractions longer than the minor-unit exponent are rounded half-up. Returns
 * `null` for anything that isn't a parseable number.
 *
 * Note: a single comma with no other separator is treated as a decimal comma
 * (Polish convention), so "1,5" -> 150, not 1.50-thousands.
 */
export function parseAmountToMinor(input: string): number | null {
  if (typeof input !== 'string') return null
  let s = input.trim()
  if (s === '') return null

  // Optional leading sign.
  let negative = false
  if (s.startsWith('-')) {
    negative = true
    s = s.slice(1)
  } else if (s.startsWith('+')) {
    s = s.slice(1)
  }

  // Drop every flavour of space — these are only ever thousands separators.
  // JS \s already covers NBSP (U+00A0), narrow NBSP (U+202F) and thin space.
  s = s.replace(/\s/g, '')
  if (s === '') return null

  // After sign + spaces are gone, only digits and . , may remain.
  if (!/^[0-9.,]+$/.test(s)) return null

  const commaCount = (s.match(/,/g) ?? []).length
  const dotCount = (s.match(/\./g) ?? []).length

  // Decide which character is the decimal separator (if any).
  let decimalSep: '.' | ',' | null
  if (commaCount > 0 && dotCount > 0) {
    // Whichever appears last is the decimal separator; the other is thousands.
    decimalSep = s.lastIndexOf(',') > s.lastIndexOf('.') ? ',' : '.'
  } else if (commaCount === 1) {
    decimalSep = ','
  } else if (dotCount === 1) {
    decimalSep = '.'
  } else {
    // None, or several of one kind (e.g. "1.234.567") -> all thousands.
    decimalSep = null
  }

  let intPart: string
  let fracPart: string
  if (decimalSep === null) {
    intPart = s.replace(/[.,]/g, '')
    fracPart = ''
  } else {
    const otherSep = decimalSep === ',' ? '.' : ','
    const cleaned = s.split(otherSep).join('') // strip thousands separators
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
  /** BCP-47 locale. Defaults to pl-PL (app's primary context). */
  locale?: string
  /** How to render the currency. 'none' = plain number, no symbol/code. */
  currencyDisplay?: 'symbol' | 'code' | 'none'
  /** Sign rendering, passed through to Intl.NumberFormat. */
  signDisplay?: 'auto' | 'always' | 'exceptZero' | 'never'
}

/**
 * Format integer minor units for display. This is the ONLY place we divide by
 * 100, and that division feeds Intl purely for rendering — never for storage.
 */
export function formatMinor(
  minor: number,
  currency: string = 'PLN',
  opts: FormatMoneyOptions = {},
): string {
  const { locale = 'pl-PL', currencyDisplay = 'symbol', signDisplay = 'auto' } = opts
  const value = minor / MINOR_UNIT_FACTOR

  if (currencyDisplay === 'none') {
    return new Intl.NumberFormat(locale, {
      style: 'decimal',
      minimumFractionDigits: MINOR_UNIT_EXPONENT,
      maximumFractionDigits: MINOR_UNIT_EXPONENT,
      // Force thousands grouping: the ES2023 "auto" default skips it for
      // 4-digit values in some locales (e.g. pl-PL), which reads poorly.
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

/**
 * Convert a native-currency amount into base (reporting) minor units.
 * Matches the data model: base value = round(amountMinor * rateToBase).
 */
export function toBaseMinor(amountMinor: number, rateToBase: number): number {
  return Math.round(amountMinor * rateToBase)
}

/** Sum integer minor-unit values (exact integer arithmetic). */
export function sumMinor(values: number[]): number {
  return values.reduce((acc, v) => acc + v, 0)
}

/** True when the input parses to a positive amount (what a txn entry needs). */
export function isValidAmountInput(input: string): boolean {
  const minor = parseAmountToMinor(input)
  return minor !== null && minor > 0
}
