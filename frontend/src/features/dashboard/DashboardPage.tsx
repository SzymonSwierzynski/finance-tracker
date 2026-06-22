import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Button, Card, CenteredState, Input, PageHeader, Skeleton } from '@/components/primitives'
import { Money, useFormatMoney } from '@/components/Money'
import { formatDate, presetRange, todayIso } from '@/lib/date'
import type { DateRange, PeriodPresetId } from '@/lib/date'
import { toMajorNumber } from '@/lib/money'
import { localeForLanguage } from '@/lib/i18n'
import { useSummary } from '@/features/reports/hooks'
import { useTransactions } from '@/features/transactions/hooks'

const PRESETS: PeriodPresetId[] = ['thisMonth', 'lastMonth', 'thisYear', 'custom']

function StatCard({ label, minor, currency, tone }: { label: string; minor: number; currency: string; tone: 'income' | 'expense' | 'net' }) {
  const ring = tone === 'income' ? 'ring-positive/20' : tone === 'expense' ? 'ring-negative/20' : 'ring-brand-200'
  return (
    <Card className={`p-5 ring-1 ${ring}`}>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-2 text-2xl font-semibold">
        <Money minor={minor} currency={currency} colored={tone !== 'net'} />
      </p>
    </Card>
  )
}

export function DashboardPage() {
  const { t, i18n } = useTranslation()
  const locale = localeForLanguage(i18n.language)
  const formatMoney = useFormatMoney()

  const [preset, setPreset] = useState<PeriodPresetId>('thisMonth')
  const [range, setRange] = useState<DateRange>(() => presetRange('thisMonth'))

  const onPreset = (p: PeriodPresetId) => {
    setPreset(p)
    if (p !== 'custom') setRange(presetRange(p))
  }

  const { data: summary, isLoading, isError, refetch } = useSummary(range.from, range.to)
  const { data: recent } = useTransactions({ from: range.from, to: range.to, page: 0, size: 5 })

  const currency = summary?.currency ?? 'PLN'
  const chartData = useMemo(
    () => [
      { name: t('dashboard.income'), value: toMajorNumber(summary?.incomeMinor ?? 0), fill: 'var(--color-positive)' },
      { name: t('dashboard.expense'), value: toMajorNumber(summary?.expenseMinor ?? 0), fill: 'var(--color-negative)' },
    ],
    [summary, t],
  )

  const hasActivity = (summary?.incomeMinor ?? 0) !== 0 || (summary?.expenseMinor ?? 0) !== 0

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
            <span className="text-slate-400">–</span>
            <Input type="date" value={range.to} onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} className="w-auto" />
          </div>
        )}
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
                <StatCard label={t('dashboard.income')} minor={summary.incomeMinor} currency={currency} tone="income" />
                <StatCard label={t('dashboard.expense')} minor={summary.expenseMinor} currency={currency} tone="expense" />
                <StatCard label={t('dashboard.net')} minor={summary.netMinor} currency={currency} tone="net" />
              </>
            )}
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <Card className="p-5">
              <h2 className="mb-4 text-sm font-semibold text-slate-700">{t('dashboard.incomeVsExpense')}</h2>
              {!hasActivity ? (
                <p className="py-12 text-center text-sm text-slate-400">{t('dashboard.empty')}</p>
              ) : (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
                      <XAxis dataKey="name" tickLine={false} axisLine={false} fontSize={12} />
                      <YAxis tickLine={false} axisLine={false} width={70} fontSize={11} tickFormatter={(v: number) => formatMoney(Math.round(v * 100), currency, { currencyDisplay: 'none' })} />
                      <Tooltip formatter={(v) => formatMoney(Math.round(Number(v) * 100), currency)} cursor={{ fill: 'rgba(0,0,0,0.04)' }} />
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
              <h2 className="mb-4 text-sm font-semibold text-slate-700">{t('dashboard.recent')}</h2>
              {!recent || recent.items.length === 0 ? (
                <p className="py-12 text-center text-sm text-slate-400">{t('dashboard.empty')}</p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {recent.items.map((tx) => (
                    <li key={tx.id} className="flex items-center justify-between py-2.5">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-slate-800">{tx.description || t(`transactions.${tx.type}`)}</p>
                        <p className="text-xs text-slate-400">{formatDate(tx.date, locale)}</p>
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
