import { canStep, periodBounds, periodLabel, shiftPeriod } from '@/lib/period'
import type { Period } from '@/lib/period'
import { currentMonth } from '@/lib/date'
import { format } from 'date-fns'

interface PeriodSelectorProps {
  period: Period
  onChange: (period: Period) => void
}

const TYPES: { value: Period['type']; label: string }[] = [
  { value: 'month', label: 'Month' },
  { value: 'year', label: 'Year' },
  { value: 'all', label: 'All' },
  { value: 'custom', label: 'Custom' },
]

const stepBtn = 'rounded-md px-2 py-1 text-slate-600 hover:bg-slate-100'
const dateCls = 'rounded-lg border border-slate-300 px-2 py-1 text-sm outline-none focus:border-slate-500'

export function PeriodSelector({ period, onChange }: PeriodSelectorProps) {
  function selectType(type: Period['type']) {
    if (type === period.type) return
    switch (type) {
      case 'month':
        onChange({ type: 'month', anchor: currentMonth() })
        break
      case 'year':
        onChange({ type: 'year', anchor: format(new Date(), 'yyyy') })
        break
      case 'all':
        onChange({ type: 'all' })
        break
      case 'custom': {
        const b = periodBounds({ type: 'month', anchor: currentMonth() })
        onChange({ type: 'custom', start: b.start, end: b.end })
        break
      }
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="inline-flex rounded-lg border border-slate-200 bg-white p-0.5">
        {TYPES.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => selectType(t.value)}
            className={`rounded-md px-3 py-1 text-sm font-medium ${
              period.type === t.value ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {canStep(period) && (
        <div className="flex items-center gap-1">
          <button type="button" aria-label="Previous period" onClick={() => onChange(shiftPeriod(period, -1))} className={stepBtn}>
            ‹
          </button>
          <span className="min-w-[7.5rem] text-center text-sm font-medium text-slate-800">
            {periodLabel(period)}
          </span>
          <button type="button" aria-label="Next period" onClick={() => onChange(shiftPeriod(period, 1))} className={stepBtn}>
            ›
          </button>
        </div>
      )}

      {period.type === 'all' && <span className="text-sm font-medium text-slate-800">All time</span>}

      {period.type === 'custom' && (
        <div className="flex items-center gap-2">
          <input
            type="date"
            value={period.start}
            onChange={(e) => onChange({ type: 'custom', start: e.target.value, end: period.end })}
            className={dateCls}
          />
          <span className="text-slate-400">→</span>
          <input
            type="date"
            value={period.end}
            onChange={(e) => onChange({ type: 'custom', start: period.start, end: e.target.value })}
            className={dateCls}
          />
        </div>
      )}
    </div>
  )
}
