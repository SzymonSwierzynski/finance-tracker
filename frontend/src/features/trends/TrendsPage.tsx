import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { TrendInterval } from '@/api'
import { Button, Card, CenteredState, Input, PageHeader, Skeleton } from '@/components/primitives'
import { useFormatMoney } from '@/components/Money'
import { formatDate, presetRange, todayIso } from '@/lib/date'
import type { DateRange, PeriodPresetId } from '@/lib/date'
import { toMajorNumber } from '@/lib/money'
import { localeForLanguage } from '@/lib/i18n'
import { useCashflow } from '@/features/reports/hooks'

const PRESETS: PeriodPresetId[] = ['thisMonth', 'lastMonth', 'thisYear', 'custom']
const INTERVALS: TrendInterval[] = ['month', 'week']
const COLORS = { income: '#22c55e', expense: '#ef4444', net: '#4f46e5' }

export function TrendsPage() {
  const { t, i18n } = useTranslation()
  const locale = localeForLanguage(i18n.language)
  const formatMoney = useFormatMoney()

  const [preset, setPreset] = useState<PeriodPresetId>('thisYear')
  const [range, setRange] = useState<DateRange>(() => presetRange('thisYear'))
  const [grouping, setGrouping] = useState<TrendInterval>('month')

  const onPreset = (p: PeriodPresetId) => {
    setPreset(p)
    if (p !== 'custom') setRange(presetRange(p))
  }

  const { data, isLoading, isError, refetch } = useCashflow(range.from, range.to, grouping)
  const currency = data?.currency ?? 'PLN'

  // running net (cumulative) is the line; income/expense are grouped bars.
  const chartData = (data?.buckets ?? []).map((b) => ({
    period: b.period,
    income: toMajorNumber(b.incomeMinor),
    expense: toMajorNumber(b.expenseMinor),
    net: toMajorNumber(b.runningNetMinor),
  }))
  const hasActivity = chartData.some((d) => d.income !== 0 || d.expense !== 0)
  const money = (major: number) => formatMoney(Math.round(major * 100), currency)

  return (
    <>
      <PageHeader
        title={t('trends.title')}
        subtitle={`${formatDate(range.from, locale)} – ${formatDate(range.to, locale)}`}
        actions={
          <div className="flex rounded-lg bg-slate-100 p-0.5">
            {INTERVALS.map((iv) => (
              <button
                key={iv}
                onClick={() => setGrouping(iv)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium ${grouping === iv ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500'}`}
              >
                {t(`trends.${iv}`)}
              </button>
            ))}
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
            <span className="text-slate-400">–</span>
            <Input
              type="date"
              value={range.to}
              onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
              className="w-auto"
            />
          </div>
        )}
      </div>

      {isLoading ? (
        <Card className="p-6">
          <Skeleton className="h-80 w-full" />
        </Card>
      ) : isError ? (
        <CenteredState
          title={t('errors.loadFailed')}
          action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>}
        />
      ) : !hasActivity ? (
        <CenteredState title={t('trends.empty')} />
      ) : (
        <Card className="p-5">
          <div className="h-96">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={chartData} margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="period" tick={{ fontSize: 12 }} />
                <YAxis
                  tick={{ fontSize: 12 }}
                  width={80}
                  tickFormatter={(v) => money(Number(v))}
                />
                <Tooltip
                  formatter={(value, name) => [money(Number(value)), t(`trends.${String(name)}`)]}
                />
                <Legend formatter={(name) => t(`trends.${String(name)}`)} />
                <Bar dataKey="income" fill={COLORS.income} radius={[3, 3, 0, 0]} />
                <Bar dataKey="expense" fill={COLORS.expense} radius={[3, 3, 0, 0]} />
                <Line dataKey="net" stroke={COLORS.net} strokeWidth={2} dot={false} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        </Card>
      )}
    </>
  )
}
