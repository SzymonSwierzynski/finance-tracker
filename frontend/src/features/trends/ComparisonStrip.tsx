import { useTranslation } from 'react-i18next'
import type { TrendComparison } from '@/api'
import { StatCard } from '@/components/StatCard'

/** Income / expenses / net for the selected range, each with Δ vs the previous equal-length period. */
export function ComparisonStrip({ data, currency }: { data: TrendComparison; currency: string }) {
  const { t } = useTranslation()
  const label = t('trends.vsPrev')
  return (
    <div className="mb-6 grid gap-4 sm:grid-cols-3">
      <StatCard
        label={t('trends.income')}
        minor={data.current.incomeMinor}
        currency={currency}
        tone="income"
        previousMinor={data.previous.incomeMinor}
        goodWhenUp
        compareLabel={label}
      />
      <StatCard
        label={t('trends.expenseTotal')}
        minor={data.current.expenseMinor}
        currency={currency}
        tone="expense"
        previousMinor={data.previous.expenseMinor}
        goodWhenUp={false}
        compareLabel={label}
      />
      <StatCard
        label={t('trends.netTotal')}
        minor={data.current.netMinor}
        currency={currency}
        tone="net"
        previousMinor={data.previous.netMinor}
        goodWhenUp
        compareLabel={label}
      />
    </div>
  )
}
