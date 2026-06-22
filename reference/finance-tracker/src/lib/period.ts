import {
  addMonths,
  addYears,
  endOfMonth,
  endOfYear,
  format,
  parseISO,
  startOfMonth,
  startOfYear,
} from 'date-fns'
import { currentMonth, monthLabel } from './date'

/** A reporting period for breakdown/trend views. */
export type Period =
  | { type: 'month'; anchor: string } // anchor: 'YYYY-MM'
  | { type: 'year'; anchor: string } // anchor: 'YYYY'
  | { type: 'all' }
  | { type: 'custom'; start: string; end: string } // 'YYYY-MM-DD' inclusive

export interface DateRange {
  start: string
  end: string
}

// Sentinel bounds for "all time" — date strings sort lexicographically.
const ALL_START = '0000-01-01'
const ALL_END = '9999-12-31'

/** Inclusive 'YYYY-MM-DD' date bounds for a period. */
export function periodBounds(p: Period): DateRange {
  switch (p.type) {
    case 'month': {
      const ref = parseISO(`${p.anchor}-01`)
      return {
        start: format(startOfMonth(ref), 'yyyy-MM-dd'),
        end: format(endOfMonth(ref), 'yyyy-MM-dd'),
      }
    }
    case 'year': {
      const ref = parseISO(`${p.anchor}-01-01`)
      return {
        start: format(startOfYear(ref), 'yyyy-MM-dd'),
        end: format(endOfYear(ref), 'yyyy-MM-dd'),
      }
    }
    case 'all':
      return { start: ALL_START, end: ALL_END }
    case 'custom':
      return { start: p.start, end: p.end }
  }
}

export function periodLabel(p: Period): string {
  switch (p.type) {
    case 'month':
      return monthLabel(p.anchor)
    case 'year':
      return p.anchor
    case 'all':
      return 'All time'
    case 'custom':
      return `${p.start} → ${p.end}`
  }
}

/** Step a month/year period by `delta` units; no-op for all/custom. */
export function shiftPeriod(p: Period, delta: number): Period {
  if (p.type === 'month') {
    return { type: 'month', anchor: format(addMonths(parseISO(`${p.anchor}-01`), delta), 'yyyy-MM') }
  }
  if (p.type === 'year') {
    return { type: 'year', anchor: format(addYears(parseISO(`${p.anchor}-01-01`), delta), 'yyyy') }
  }
  return p
}

/** Whether prev/next stepping applies to this period type. */
export function canStep(p: Period): boolean {
  return p.type === 'month' || p.type === 'year'
}

export const currentMonthPeriod = (): Period => ({ type: 'month', anchor: currentMonth() })
export const currentYearPeriod = (): Period => ({ type: 'year', anchor: format(new Date(), 'yyyy') })
