import { monthLabel, shiftMonth } from '@/lib/date'

interface MonthSwitcherProps {
  month: string
  onChange: (month: string) => void
}

/** Prev / next month navigator showing the current 'YYYY-MM' as a label. */
export function MonthSwitcher({ month, onChange }: MonthSwitcherProps) {
  return (
    <div className="flex items-center gap-1">
      <button
        type="button"
        onClick={() => onChange(shiftMonth(month, -1))}
        aria-label="Previous month"
        className="rounded-md px-2 py-1 text-slate-600 hover:bg-slate-100"
      >
        ‹
      </button>
      <span className="min-w-[9rem] text-center text-sm font-medium text-slate-800">
        {monthLabel(month)}
      </span>
      <button
        type="button"
        onClick={() => onChange(shiftMonth(month, 1))}
        aria-label="Next month"
        className="rounded-md px-2 py-1 text-slate-600 hover:bg-slate-100"
      >
        ›
      </button>
    </div>
  )
}
