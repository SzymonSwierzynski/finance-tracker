import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import type { BreakdownParent, CategoryKind } from '@/api'
import { CATEGORY_KINDS } from '@/api'
import { Button, Card, CenteredState, Input, PageHeader, Skeleton } from '@/components/primitives'
import { Money } from '@/components/Money'
import { presetRange, formatDate, todayIso } from '@/lib/date'
import type { DateRange, PeriodPresetId } from '@/lib/date'
import { shades } from '@/lib/color'
import { toMajorNumber } from '@/lib/money'
import { localeForLanguage } from '@/lib/i18n'
import { useBreakdown } from '@/features/reports/hooks'
import { useTheme, chartColors } from '@/lib/theme'

const PRESETS: PeriodPresetId[] = ['thisMonth', 'lastMonth', 'thisYear', 'custom']

export function BreakdownPage() {
  const { t, i18n } = useTranslation()
  const locale = localeForLanguage(i18n.language)
  const cc = chartColors(useTheme().theme)

  const [preset, setPreset] = useState<PeriodPresetId>('thisMonth')
  const [range, setRange] = useState<DateRange>(() => presetRange('thisMonth'))
  const [kind, setKind] = useState<CategoryKind>('expense')
  const [selectedId, setSelectedId] = useState<number | null | undefined>(undefined)

  const onPreset = (p: PeriodPresetId) => {
    setPreset(p)
    if (p !== 'custom') setRange(presetRange(p))
  }

  const { data, isLoading, isError, refetch } = useBreakdown(range.from, range.to, kind)
  const currency = data?.currency ?? 'PLN'
  const parents = data?.parents ?? []

  const selected: BreakdownParent | undefined = useMemo(
    () => parents.find((p) => p.categoryId === selectedId),
    [parents, selectedId],
  )

  const parentPie = parents.map((p) => ({
    name: p.name,
    value: toMajorNumber(p.baseMinor),
    color: p.color,
  }))

  const childColors = selected ? shades(selected.color, selected.children.length) : []

  return (
    <>
      <PageHeader
        title={t('breakdown.title')}
        subtitle={`${formatDate(range.from, locale)} – ${formatDate(range.to, locale)}`}
        actions={
          <div className="flex rounded-lg bg-surface-2 p-0.5">
            {CATEGORY_KINDS.map((k) => (
              <button
                key={k}
                onClick={() => {
                  setKind(k)
                  setSelectedId(undefined)
                }}
                className={`rounded-md px-3 py-1.5 text-sm font-medium ${kind === k ? 'bg-surface text-fg shadow-sm' : 'text-fg-soft'}`}
              >
                {t(`categories.${k}`)}
              </button>
            ))}
          </div>
        }
      />

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
      </div>

      {isLoading ? (
        <Card className="p-6">
          <Skeleton className="mx-auto h-48 w-48 rounded-full" />
        </Card>
      ) : isError ? (
        <CenteredState title={t('errors.loadFailed')} action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>} />
      ) : parents.length === 0 ? (
        <CenteredState title={t('breakdown.empty')} />
      ) : (
        <div className="grid gap-6 lg:grid-cols-2">
          {/* Donut */}
          <Card className="relative p-5">
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={parentPie} dataKey="value" nameKey="name" innerRadius={70} outerRadius={110} paddingAngle={2}>
                    {parentPie.map((d, i) => (
                      <Cell key={i} fill={d.color} stroke={cc.surface} />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="pointer-events-none absolute inset-x-0 top-1/2 -translate-y-1/2 text-center">
              <p className="text-xs text-fg-subtle">{t(`categories.${kind}`)}</p>
              <p className="text-lg font-semibold">
                <Money minor={data?.totalBaseMinor ?? 0} currency={currency} />
              </p>
            </div>
          </Card>

          {/* Legend / drill-down */}
          <Card className="p-5">
            {!selected ? (
              <>
                <h2 className="mb-3 text-sm font-semibold text-fg-muted">{t('breakdown.categories')}</h2>
                <ul className="divide-y divide-border-subtle">
                  {parents.map((p) => (
                    <li key={p.categoryId ?? 'uncat'}>
                      <button
                        className="flex w-full items-center justify-between py-2.5 text-left hover:opacity-80"
                        onClick={() => setSelectedId(p.categoryId)}
                        disabled={p.children.length === 0}
                      >
                        <span className="flex items-center gap-2 text-sm text-fg">
                          <span className="inline-block size-3 rounded-full" style={{ backgroundColor: p.color }} />
                          {p.name === 'Uncategorized' ? t('breakdown.uncategorized') : p.name}
                          {p.children.length > 0 && <span className="text-xs text-fg-subtle">›</span>}
                        </span>
                        <span className="flex items-center gap-3">
                          <span className="text-xs text-fg-subtle">{(p.share * 100).toFixed(0)}%</span>
                          <Money minor={p.baseMinor} currency={currency} className="text-sm font-medium" />
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <>
                <div className="mb-3 flex items-center justify-between">
                  <button onClick={() => setSelectedId(undefined)} className="text-sm text-accent hover:text-accent">
                    ‹ {t('breakdown.allCategories')}
                  </button>
                  {selected.categoryId != null && (
                    <Link to={`/transactions?categoryId=${selected.categoryId}`} className="text-sm text-accent hover:text-accent">
                      {t('breakdown.viewTransactions')}
                    </Link>
                  )}
                </div>
                <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-fg-muted">
                  <span className="inline-block size-3 rounded-full" style={{ backgroundColor: selected.color }} />
                  {selected.name}
                </h2>
                <ul className="divide-y divide-border-subtle">
                  {selected.children.map((c, i) => (
                    <li key={`${c.categoryId}-${c.name}`} className="flex items-center justify-between py-2.5">
                      <span className="flex items-center gap-2 text-sm text-fg">
                        <span className="inline-block size-3 rounded-full" style={{ backgroundColor: childColors[i] ?? selected.color }} />
                        {c.name}
                      </span>
                      <span className="flex items-center gap-3">
                        <span className="text-xs text-fg-subtle">{(c.share * 100).toFixed(0)}%</span>
                        <Money minor={c.baseMinor} currency={currency} className="text-sm font-medium" />
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Card>
        </div>
      )}
    </>
  )
}
