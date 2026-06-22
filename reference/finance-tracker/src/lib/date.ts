import { addMonths, format, parseISO } from 'date-fns'

/** Today as 'YYYY-MM-DD' (local). */
export function todayISO(): string {
  return format(new Date(), 'yyyy-MM-dd')
}

/** Current month as 'YYYY-MM' (local). */
export function currentMonth(): string {
  return format(new Date(), 'yyyy-MM')
}

/** Shift a 'YYYY-MM' month by a number of months. */
export function shiftMonth(month: string, delta: number): string {
  return format(addMonths(parseISO(`${month}-01`), delta), 'yyyy-MM')
}

/** Human label for a 'YYYY-MM' month, e.g. "May 2026". */
export function monthLabel(month: string): string {
  return format(parseISO(`${month}-01`), 'LLLL yyyy')
}
