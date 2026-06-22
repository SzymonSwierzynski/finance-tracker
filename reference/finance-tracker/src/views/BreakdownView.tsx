import { useEffect, useState } from 'react'
import { useCategoryBreakdown, useSettings } from '@/data'
import type { CategoryBreakdown, CategoryKind } from '@/data'
import { currentMonthPeriod, periodBounds } from '@/lib/period'
import type { DateRange, Period } from '@/lib/period'
import { shades } from '@/lib/color'
import { formatMinor } from '@/lib/money'
import { PeriodSelector } from '@/components/PeriodSelector'
import { CategoryDonut } from '@/components/CategoryDonut'
import type { DonutSlice } from '@/components/CategoryDonut'
import { TransactionList } from '@/components/TransactionList'

type Drill =
  | { level: 'top' }
  | { level: 'parent'; parentId: string }
  | { level: 'leaf'; categoryId: string | null; label: string }

const KINDS: { value: CategoryKind; label: string }[] = [
  { value: 'expense', label: 'Expenses' },
  { value: 'income', label: 'Income' },
]

export function BreakdownView() {
  const [period, setPeriod] = useState<Period>(currentMonthPeriod())
  const [kind, setKind] = useState<CategoryKind>('expense')
  const [drill, setDrill] = useState<Drill>({ level: 'top' })

  const range = periodBounds(period)
  const breakdown = useCategoryBreakdown(range, kind)
  const settings = useSettings()
  const currency = settings?.reportingCurrency ?? 'PLN'

  // Drilling is relative to the data shown, so reset it when that changes.
  useEffect(() => {
    setDrill({ level: 'top' })
  }, [range.start, range.end, kind])

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex rounded-lg border border-slate-200 bg-white p-0.5">
          {KINDS.map((k) => (
            <button
              key={k.value}
              type="button"
              onClick={() => setKind(k.value)}
              className={`rounded-md px-3 py-1 text-sm font-medium ${
                kind === k.value ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {k.label}
            </button>
          ))}
        </div>
        <PeriodSelector period={period} onChange={setPeriod} />
      </div>

      {!breakdown ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : breakdown.parents.length === 0 ? (
        <p className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
          No {kind === 'expense' ? 'expenses' : 'income'} in this period.
        </p>
      ) : (
        <BreakdownBody
          breakdown={breakdown}
          currency={currency}
          range={range}
          kind={kind}
          drill={drill}
          setDrill={setDrill}
        />
      )}
    </div>
  )
}

interface BodyProps {
  breakdown: CategoryBreakdown
  currency: string
  range: DateRange
  kind: CategoryKind
  drill: Drill
  setDrill: (d: Drill) => void
}

function BreakdownBody({ breakdown, currency, range, kind, drill, setDrill }: BodyProps) {
  if (drill.level === 'leaf') {
    return (
      <div className="space-y-3">
        <Breadcrumb items={[{ label: 'All', onClick: () => setDrill({ level: 'top' }) }, { label: drill.label }]} />
        <TransactionList
          filter={{ start: range.start, end: range.end, categoryId: drill.categoryId, type: kind }}
          emptyMessage="No transactions."
        />
      </div>
    )
  }

  const parent =
    drill.level === 'parent'
      ? (breakdown.parents.find((p) => p.categoryId === drill.parentId) ?? null)
      : null

  if (parent) {
    const colors = shades(parent.color, parent.children.length)
    const slices: DonutSlice[] = parent.children.map((c, i) => ({
      id: c.categoryId,
      name: c.name,
      color: colors[i] ?? parent.color,
      baseMinor: c.baseMinor,
    }))
    const select = (id: string | null) => {
      const child = parent.children.find((c) => c.categoryId === id)
      setDrill({ level: 'leaf', categoryId: id, label: child?.name ?? parent.name })
    }
    return (
      <div className="space-y-4">
        <Breadcrumb items={[{ label: 'All', onClick: () => setDrill({ level: 'top' }) }, { label: parent.name }]} />
        <DonutWithList
          slices={slices}
          currency={currency}
          total={parent.baseMinor}
          centerTitle={parent.name}
          hint="Select a subcategory to see its transactions."
          onSelect={select}
        />
      </div>
    )
  }

  // Top level.
  const slices: DonutSlice[] = breakdown.parents.map((p) => ({
    id: p.categoryId,
    name: p.name,
    color: p.color,
    baseMinor: p.baseMinor,
  }))
  const select = (id: string | null) => {
    const p = breakdown.parents.find((x) => x.categoryId === id)
    if (!p) return
    if (p.categoryId !== null && p.children.length > 0) {
      setDrill({ level: 'parent', parentId: p.categoryId })
    } else {
      setDrill({ level: 'leaf', categoryId: p.categoryId, label: p.name })
    }
  }
  return (
    <DonutWithList
      slices={slices}
      currency={currency}
      total={breakdown.totalBaseMinor}
      centerTitle="Total"
      hint="Select a category to drill in."
      onSelect={select}
    />
  )
}

interface DonutWithListProps {
  slices: DonutSlice[]
  currency: string
  total: number
  centerTitle: string
  hint?: string
  onSelect: (id: string | null) => void
}

function DonutWithList({ slices, currency, total, centerTitle, hint, onSelect }: DonutWithListProps) {
  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <CategoryDonut
          slices={slices}
          currency={currency}
          totalBaseMinor={total}
          centerTitle={centerTitle}
          onSelect={onSelect}
        />
      </div>
      <div className="rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
        <ul className="divide-y divide-slate-100">
          {slices.map((s, i) => (
            <li key={s.id ?? `null-${i}`}>
              <button
                type="button"
                onClick={() => onSelect(s.id)}
                className="flex w-full items-center justify-between gap-3 rounded-md px-2 py-2 text-left hover:bg-slate-50"
              >
                <span className="flex min-w-0 items-center gap-2">
                  <span className="h-3 w-3 shrink-0 rounded-sm" style={{ backgroundColor: s.color }} />
                  <span className="truncate text-sm text-slate-700">{s.name}</span>
                </span>
                <span className="flex shrink-0 items-center gap-3">
                  <span className="text-sm font-medium text-slate-800">{formatMinor(s.baseMinor, currency)}</span>
                  <span className="w-12 text-right text-xs text-slate-400">
                    {total ? ((s.baseMinor / total) * 100).toFixed(1) : '0.0'}%
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
        {hint && <p className="px-2 pb-1 pt-2 text-xs text-slate-400">{hint}</p>}
      </div>
    </div>
  )
}

function Breadcrumb({ items }: { items: { label: string; onClick?: () => void }[] }) {
  return (
    <nav className="flex items-center gap-1 text-sm">
      {items.map((it, i) => (
        <span key={i} className="flex items-center gap-1">
          {i > 0 && <span className="text-slate-300">▸</span>}
          {it.onClick ? (
            <button type="button" onClick={it.onClick} className="text-slate-500 hover:text-slate-800">
              {it.label}
            </button>
          ) : (
            <span className="font-medium text-slate-800">{it.label}</span>
          )}
        </span>
      ))}
    </nav>
  )
}
