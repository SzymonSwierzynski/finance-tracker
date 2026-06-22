import { format, isValid, parse } from 'date-fns'

/** Formats tried in order when the mapping asks for 'auto'. */
export const AUTO_DATE_FORMATS = ['yyyy-MM-dd', 'dd.MM.yyyy', 'dd-MM-yyyy', 'dd/MM/yyyy', 'yyyy/MM/dd']

/** Options offered in the import UI. */
export const DATE_FORMAT_OPTIONS = ['auto', ...AUTO_DATE_FORMATS]

/**
 * Parse a bank-export date into 'YYYY-MM-DD', or null if it doesn't match.
 * Tries the given date-fns format (or the common Polish set for 'auto'). A
 * round-trip check rejects lenient/rolled-over parses like '2026-13-40'.
 */
export function parseFlexibleDate(value: string | undefined, fmt: string): string | null {
  const s = (value ?? '').trim()
  if (!s) return null
  const formats = fmt && fmt !== 'auto' ? [fmt] : AUTO_DATE_FORMATS
  for (const f of formats) {
    const d = parse(s, f, new Date())
    if (isValid(d) && format(d, f) === s) {
      return format(d, 'yyyy-MM-dd')
    }
  }
  return null
}
