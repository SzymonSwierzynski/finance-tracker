import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Bar,
  BarChart,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from 'recharts'
import type { CategorySeries, TrendInterval } from '@/api'
import { Button, Card, CenteredState, Input, PageHeader, Skeleton } from '@/components/primitives'
import { useFormatMoney } from '@/components/Money'
import { formatDate, presetRange, todayIso } from '@/lib/date'
import type { DateRange, PeriodPresetId } from '@/lib/date'
import { toMajorNumber } from '@/lib/money'
import { localeForLanguage } from '@/lib/i18n'
import { useCashflow, useCategoryTrend } from '@/features/reports/hooks'
import { useTheme, chartColors } from '@/lib/theme'

const PRESETS: PeriodPresetId[] = ['thisMonth', 'lastMonth', 'thisYear', 'custom']
const INTERVALS: TrendInterval[] = ['month', 'week']
const VIEWS = ['total', 'category'] as const
const COLORS = { income: '#22c55e', expense: '#ef4444', net: '#4f46e5' }

const pill = (active: boolean) =>
  `rounded-md px-3 py-1.5 text-sm font-medium ${active ? 'bg-surface text-fg shadow-sm' : 'text-fg-soft'}`

const seriesKey = (s: CategorySeries) =>
  s.categoryId != null ? String(s.categoryId) : 'uncategorized'

export function TrendsPage() {
  const { t, i18n } = useTranslation()
  const locale = localeForLanguage(i18n.language)
  const formatMoney = useFormatMoney()
  const cc = chartColors(useTheme().theme)

  const [preset, setPreset] = useState<PeriodPresetId>('thisYear')
  const [range, setRange] = useState<DateRange>(() => presetRange('thisYear'))
  const [grouping, setGrouping] = useState<TrendInterval>('month')
  const [view, setView] = useState<(typeof VIEWS)[number]>('total')

  const onPreset = (p: PeriodPresetId) => {
    setPreset(p)
    if (p !== 'custom') setRange(presetRange(p))
  }

  // Only the active view fetches; both share range + grouping so switching is instant once cached.
  const cashflow = useCashflow(range.from, range.to, grouping, view === 'total')
  const catTrend = useCategoryTrend(range.from, range.to, grouping, 'expense', view === 'category')
  const active = view === 'total' ? cashflow : catTrend
  const currency =
    (view === 'total' ? cashflow.data?.currency : catTrend.data?.currency) ?? 'PLN'
  const money = (major: number) => formatMoney(Math.round(major * 100), currency)

  const totalData = (cashflow.data?.buckets ?? []).map((b) => ({
    period: b.period,
    income: toMajorNumber(b.incomeMinor),
    expense: toMajorNumber(b.expenseMinor),
    net: toMajorNumber(b.runningNetMinor),
  }))
  const catSeries = catTrend.data?.series ?? []
  const catData = (catTrend.data?.buckets ?? []).map((b) => {
    const row: Record<string, string | number> = { period: b.period }
    for (const s of catSeries) row[seriesKey(s)] = toMajorNumber(b.amounts[seriesKey(s)] ?? 0)
    return row
  })

  const hasActivity =
    view === 'total'
      ? totalData.some((d) => d.income !== 0 || d.expense !== 0)
      : catSeries.length > 0

  return (
    <>
      <PageHeader
        title={t('trends.title')}
        subtitle={`${formatDate(range.from, locale)} – ${formatDate(range.to, locale)}`}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex rounded-lg bg-surface-2 p-0.5">
              {VIEWS.map((v) => (
                <button key={v} onClick={() => setView(v)} className={pill(view === v)}>
                  {t(`trends.${v}`)}
                </button>
              ))}
            </div>
            <div className="flex rounded-lg bg-surface-2 p-0.5">
              {INTERVALS.map((iv) => (
                <button key={iv} onClick={() => setGrouping(iv)} className={pill(grouping === iv)}>
                  {t(`trends.${iv}`)}
                </button>
              ))}
            </div>
          </div>
        }
      />

      <div className="mb-6 flex flex-wrap items-center gap-2">
        {PRESETS.map((p) => (
          <Button
            key={p}
            variant={preset === p ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => onPreset(p)}
          >
            {t(`dashboard.${p}`)}
          </Button>
        ))}
        {preset === 'custom' && (
          <div className="flex items-center gap-2">
            <Input
              type="date"
              value={range.from}
              max={todayIso()}
              onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
              className="w-auto"
            />
            <span className="text-fg-subtle">–</span>
            <Input
              type="date"
              value={range.to}
              onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
              className="w-auto"
            />
          </div>
        )}
      </div>

      {active.isLoading ? (
        <Card className="p-6">
          <Skeleton className="h-80 w-full" />
        </Card>
      ) : active.isError ? (
        <CenteredState
          title={t('errors.loadFailed')}
          action={<Button onClick={() => void active.refetch()}>{t('common.retry')}</Button>}
        />
      ) : !hasActivity ? (
        <CenteredState title={t('trends.empty')} />
      ) : (
        <Card className="p-5">
          <div className="h-96">
            <ResponsiveContainer width="100%" height="100%">
              {view === 'total' ? (
                <ComposedChart data={totalData} margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={cc.grid} />
                  <XAxis dataKey="period" tick={{ fontSize: 12, fill: cc.axis }} />
                  <YAxis tick={{ fontSize: 12, fill: cc.axis }} width={80} tickFormatter={(v) => money(Number(v))} />
                  <Legend formatter={(name) => t(`trends.${String(name)}`)} />
                  <Bar dataKey="income" fill={COLORS.income} radius={[3, 3, 0, 0]} />
                  <Bar dataKey="expense" fill={COLORS.expense} radius={[3, 3, 0, 0]} />
                  <Line dataKey="net" stroke={COLORS.net} strokeWidth={2} dot={false} />
                </ComposedChart>
              ) : (
                <BarChart data={catData} margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={cc.grid} />
                  <XAxis dataKey="period" tick={{ fontSize: 12, fill: cc.axis }} />
                  <YAxis tick={{ fontSize: 12, fill: cc.axis }} width={80} tickFormatter={(v) => money(Number(v))} />
                  <Legend />
                  {catSeries.map((s) => (
                    <Bar
                      key={seriesKey(s)}
                      dataKey={seriesKey(s)}
                      name={s.categoryId == null ? t('breakdown.uncategorized') : s.name}
                      stackId="a"
                      fill={s.color}
                    />
                  ))}
                </BarChart>
              )}
            </ResponsiveContainer>
          </div>
        </Card>
      )}
    </>
  )
}
