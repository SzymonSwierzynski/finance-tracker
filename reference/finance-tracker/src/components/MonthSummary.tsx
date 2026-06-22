import type { ReactNode } from 'react'
import { useMonthSummary } from '@/data'
import { Money } from './Money'

function SummaryCard({
  label,
  accent,
  children,
}: {
  label: string
  accent: string
  children: ReactNode
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${accent}`}>{children}</div>
    </div>
  )
}

interface MonthSummaryProps {
  month: string
  /** Reporting currency — all totals are already in this currency's units. */
  currency: string
}

/** This-month income / expense / net, in the reporting currency. */
export function MonthSummary({ month, currency }: MonthSummaryProps) {
  const summary = useMonthSummary(month)
  const income = summary?.incomeMinor ?? 0
  const expense = summary?.expenseMinor ?? 0
  const net = summary?.netMinor ?? 0

  return (
    <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <SummaryCard label="Income" accent="text-emerald-600">
        <Money minor={income} currency={currency} />
      </SummaryCard>
      <SummaryCard label="Expenses" accent="text-red-600">
        <Money minor={expense} currency={currency} />
      </SummaryCard>
      <SummaryCard label="Net" accent={net < 0 ? 'text-red-600' : 'text-slate-900'}>
        <Money minor={net} currency={currency} signDisplay="exceptZero" />
      </SummaryCard>
    </section>
  )
}
