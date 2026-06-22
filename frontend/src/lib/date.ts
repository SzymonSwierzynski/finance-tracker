/** Calendar-date helpers. Dates are ISO 'YYYY-MM-DD' strings (matching the backend DATE contract). */

export interface DateRange {
  from: string
  to: string
}

export type PeriodPresetId = 'thisMonth' | 'lastMonth' | 'thisYear' | 'custom'

function iso(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function todayIso(): string {
  return iso(new Date())
}

export function monthRange(ref = new Date()): DateRange {
  const start = new Date(ref.getFullYear(), ref.getMonth(), 1)
  const end = new Date(ref.getFullYear(), ref.getMonth() + 1, 0)
  return { from: iso(start), to: iso(end) }
}

export function presetRange(preset: PeriodPresetId, now = new Date()): DateRange {
  switch (preset) {
    case 'thisMonth':
      return monthRange(now)
    case 'lastMonth':
      return monthRange(new Date(now.getFullYear(), now.getMonth() - 1, 1))
    case 'thisYear':
      return { from: iso(new Date(now.getFullYear(), 0, 1)), to: iso(new Date(now.getFullYear(), 11, 31)) }
    case 'custom':
      return monthRange(now)
  }
}

/** Localized, human-readable date (e.g. "15 cze 2024"). */
export function formatDate(isoDate: string, locale = 'pl-PL'): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  if (!y || !m || !d) return isoDate
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(y, m - 1, d),
  )
}

/** Localized month + year label (e.g. "czerwiec 2024"). */
export function formatMonthLabel(isoDate: string, locale = 'pl-PL'): string {
  const [y, m] = isoDate.split('-').map(Number)
  if (!y || !m) return isoDate
  return new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(new Date(y, m - 1, 1))
}
