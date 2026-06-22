import { describe, it, expect } from 'vitest'
import { buildImportRows } from './importRows'
import type { ImportMapping } from './types'

const base: ImportMapping = {
  delimiter: ';',
  encoding: 'utf-8',
  hasHeader: true,
  dateIndex: 0,
  dateFormat: 'auto',
  descriptionIndex: 1,
  amountMode: 'signed',
  amountIndex: 2,
  expenseIsNegative: true,
  debitIndex: -1,
  creditIndex: -1,
}

describe('buildImportRows — signed amount column', () => {
  const rows = [
    ['Date', 'Title', 'Amount'],
    ['15.05.2026', 'Biedronka', '-19,99'],
    ['16.05.2026', 'Salary', '5 000,00'],
    ['bad', 'Broken', ''],
  ]
  const out = buildImportRows(rows, base)

  it('skips the header row', () => {
    expect(out).toHaveLength(3)
  })

  it('reads a negative as an expense (Polish decimal comma)', () => {
    expect(out[0]).toMatchObject({ date: '2026-05-15', amountMinor: 1999, type: 'expense', valid: true })
  })

  it('reads a positive (with space thousands) as income', () => {
    expect(out[1]).toMatchObject({ date: '2026-05-16', amountMinor: 500000, type: 'income', valid: true })
  })

  it('flags rows with a bad date and missing amount', () => {
    expect(out[2].valid).toBe(false)
    expect(out[2].error).toContain('date')
    expect(out[2].error).toContain('amount')
  })

  it('honours the opposite sign convention', () => {
    const out2 = buildImportRows(rows, { ...base, expenseIsNegative: false })
    expect(out2[0].type).toBe('income') // -19,99 now counts as income
    expect(out2[1].type).toBe('expense')
  })
})

describe('buildImportRows — separate debit/credit columns', () => {
  const mapping: ImportMapping = {
    ...base,
    amountMode: 'debitCredit',
    amountIndex: -1,
    debitIndex: 2,
    creditIndex: 3,
  }
  const rows = [
    ['Date', 'Title', 'Debit', 'Credit'],
    ['15.05.2026', 'Shop', '19,99', ''],
    ['16.05.2026', 'Payroll', '', '5 000,00'],
  ]
  const out = buildImportRows(rows, mapping)

  it('maps the debit column to an expense', () => {
    expect(out[0]).toMatchObject({ amountMinor: 1999, type: 'expense', valid: true })
  })

  it('maps the credit column to income', () => {
    expect(out[1]).toMatchObject({ amountMinor: 500000, type: 'income', valid: true })
  })
})
