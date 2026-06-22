import { parseAmountToMinor } from '@/lib/money'
import { parseFlexibleDate } from '@/lib/csvDate'
import type { ImportMapping } from './types'

/** A CSV row interpreted against a mapping, ready for preview/import. */
export interface ParsedImportRow {
  /** Source row index (for display + stable keys). */
  index: number
  date: string | null
  /** Positive minor units (native currency). */
  amountMinor: number | null
  type: 'expense' | 'income'
  description: string
  raw: string[]
  valid: boolean
  error?: string
}

function cell(row: string[], idx: number): string {
  if (idx < 0 || idx >= row.length) return ''
  return (row[idx] ?? '').trim()
}

/** Parse a cell to a positive magnitude (0 when empty/invalid). */
function magnitude(value: string): number {
  const v = parseAmountToMinor(value)
  return v == null ? 0 : Math.abs(v)
}

/**
 * Interpret raw CSV rows against a column mapping. Pure: returns one entry per
 * data row (header skipped), each flagged valid/invalid so the UI can preview
 * and let the user fix the mapping before importing.
 */
export function buildImportRows(rows: string[][], mapping: ImportMapping): ParsedImportRow[] {
  const out: ParsedImportRow[] = []
  const startAt = mapping.hasHeader ? 1 : 0

  for (let i = startAt; i < rows.length; i++) {
    const raw = rows[i]
    const description = cell(raw, mapping.descriptionIndex)
    const date = parseFlexibleDate(cell(raw, mapping.dateIndex), mapping.dateFormat)

    let amountMinor: number | null = null
    let type: 'expense' | 'income' = 'expense'

    if (mapping.amountMode === 'signed') {
      const signed = parseAmountToMinor(cell(raw, mapping.amountIndex))
      if (signed != null && signed !== 0) {
        const negative = signed < 0
        const isExpense = mapping.expenseIsNegative ? negative : !negative
        type = isExpense ? 'expense' : 'income'
        amountMinor = Math.abs(signed)
      }
    } else {
      const debit = magnitude(cell(raw, mapping.debitIndex))
      const credit = magnitude(cell(raw, mapping.creditIndex))
      if (debit > 0) {
        type = 'expense'
        amountMinor = debit
      } else if (credit > 0) {
        type = 'income'
        amountMinor = credit
      }
    }

    const problems: string[] = []
    if (!date) problems.push('date')
    if (amountMinor == null || amountMinor <= 0) problems.push('amount')
    const valid = problems.length === 0

    out.push({
      index: i,
      date,
      amountMinor,
      type,
      description,
      raw,
      valid,
      error: valid ? undefined : `Invalid ${problems.join(' & ')}`,
    })
  }

  return out
}
