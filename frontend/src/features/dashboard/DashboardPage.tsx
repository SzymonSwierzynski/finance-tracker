import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bar, BarChart, Cell, ResponsiveContainer, XAxis, YAxis } from 'recharts'
import type { ComparisonMode } from '@/api'
import { Button, Card, CenteredState, Input, PageHeader, Skeleton } from '@/components/primitives'
import { Money, useFormatMoney } from '@/components/Money'
import { StatCard } from '@/components/StatCard'
import { formatDate, presetRange, todayIso } from '@/lib/date'
import type { DateRange, PeriodPresetId } from '@/lib/date'
import { toMajorNumber } from '@/lib/money'
import { localeForLanguage } from '@/lib/i18n'
import { useTheme, chartColors } from '@/lib/theme'
import { useComparison, useSummary } from '@/features/reports/hooks'
import { useTransactions } from '@/features/transactions/hooks'

const PRESETS: PeriodPresetId[] = ['thisMonth', 'lastMonth', 'thisYear', 'custom']

export function DashboardPage() {
  const { t, i18n } = useTranslation()
  const locale = localeForLanguage(i18n.language)
  const formatMoney = useFormatMoney()

  const [preset, setPreset] = useState<PeriodPresetId>('thisMonth')
  const [range, setRange] = useState<DateRange>(() => presetRange('thisMonth'))
  const [compareMode, setCompareMode] = useState<'off' | ComparisonMode>('off')

  const onPreset = (p: PeriodPresetId) => {
    setPreset(p)
    if (p !== 'custom') setRange(presetRange(p))
  }

  const { data: summary, isLoading, isError, refetch } = useSummary(range.from, range.to)
  const { data: recent } = useTransactions({ from: range.from, to: range.to, page: 0, size: 5 })
  const comparison = useComparison(
    range.from,
    range.to,
    compareMode === 'off' ? 'month' : compareMode,
    compareMode !== 'off',
  )
  const previous = compareMode === 'off' ? undefined : comparison.data?.previous
  const compareLabel = t(compareMode === 'year' ? 'dashboard.vsLastYear' : 'dashboard.vsLastMonth')

  const currency = summary?.currency ?? 'PLN'
  const chartData = useMemo(
    () => [
      { name: t('dashboard.income'), value: toMajorNumber(summary?.incomeMinor ?? 0), fill: 'var(--color-positive)' },
      { name: t('dashboard.expense'), value: toMajorNumber(summary?.expenseMinor ?? 0), fill: 'var(--color-negative)' },
    ],
    [summary, t],
  )

  const hasActivity = (summary?.incomeMinor ?? 0) !== 0 || (summary?.expenseMinor ?? 0) !== 0
  const cc = chartColors(useTheme().theme)

  return (
    <>
      <PageHeader title={t('dashboard.title')} subtitle={`${formatDate(range.from, locale)} – ${formatDate(range.to, locale)}`} />

      <div className="mb-6 flex flex-wrap items-center gap-2">
        {PRESETS.map((p) => (
          <Button key={p} variant={preset === p ? 'primary' : 'secondary'} size="sm" onClick={() => onPreset(p)}>
            {t(`dashboard.${p}`)}
          </Button>
        ))}
        {preset === 'custom' && (
          <div className="flex items-center gap-2">
            <Input type="date" value={range.from} max={todayIso()} onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))} className="w-auto" />
            <span className="text-fg-subtle">–</span>
            <Input type="date" value={range.to} onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} className="w-auto" />
          </div>
        )}
        <div className="ml-auto flex items-center gap-1.5">
          <span className="text-xs text-fg-subtle">{t('dashboard.compare')}</span>
          {(['off', 'month', 'year'] as const).map((m) => (
            <Button key={m} variant={compareMode === m ? 'primary' : 'secondary'} size="sm" onClick={() => setCompareMode(m)}>
              {t(`dashboard.compare_${m}`)}
            </Button>
          ))}
        </div>
      </div>

      {isError ? (
        <CenteredState title={t('errors.loadFailed')} action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>} />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            {isLoading || !summary ? (
              [0, 1, 2].map((i) => (
                <Card key={i} className="p-5">
                  <Skeleton className="h-4 w-16" />
                  <Skeleton className="mt-3 h-8 w-28" />
                </Card>
              ))
            ) : (
              <>
                <StatCard label={t('dashboard.income')} minor={summary.incomeMinor} currency={currency} tone="income" previousMinor={previous?.incomeMinor} goodWhenUp compareLabel={compareLabel} />
                <StatCard label={t('dashboard.expense')} minor={summary.expenseMinor} currency={currency} tone="expense" previousMinor={previous?.expenseMinor} goodWhenUp={false} compareLabel={compareLabel} />
                <StatCard label={t('dashboard.net')} minor={summary.netMinor} currency={currency} tone="net" previousMinor={previous?.netMinor} goodWhenUp compareLabel={compareLabel} />
              </>
            )}
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <Card className="p-5">
              <h2 className="mb-4 text-sm font-semibold text-fg-muted">{t('dashboard.incomeVsExpense')}</h2>
              {!hasActivity ? (
                <p className="py-12 text-center text-sm text-fg-subtle">{t('dashboard.empty')}</p>
              ) : (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                      <XAxis dataKey="name" tickLine={false} axisLine={false} tick={{ fill: cc.axis, fontSize: 12 }} />
                      <YAxis tickLine={false} axisLine={false} width={70} tick={{ fill: cc.axis, fontSize: 11 }} tickFormatter={(v: number) => formatMoney(Math.round(v * 100), currency, { currencyDisplay: 'none' })} />
                      <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                        {chartData.map((entry) => (
                          <Cell key={entry.name} fill={entry.fill} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card className="p-5">
              <h2 className="mb-4 text-sm font-semibold text-fg-muted">{t('dashboard.recent')}</h2>
              {!recent || recent.items.length === 0 ? (
                <p className="py-12 text-center text-sm text-fg-subtle">{t('dashboard.empty')}</p>
              ) : (
                <ul className="divide-y divide-border-subtle">
                  {recent.items.map((tx) => (
                    <li key={tx.id} className="flex items-center justify-between py-2.5">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-fg">{tx.description || t(`transactions.${tx.type}`)}</p>
                        <p className="text-xs text-fg-subtle">{formatDate(tx.date, locale)}</p>
                      </div>
                      <Money
                        minor={tx.type === 'expense' ? -tx.amountMinor : tx.amountMinor}
                        currency={tx.currency}
                        colored={tx.type !== 'transfer'}
                        signDisplay={tx.type === 'transfer' ? 'auto' : 'always'}
                        className="text-sm font-medium"
                      />
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </>
      )}
    </>
  )
}
