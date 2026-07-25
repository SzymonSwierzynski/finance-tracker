import { useTranslation } from 'react-i18next'
import type { CategoryMover, TrendComparison } from '@/api'
import { Card } from '@/components/primitives'
import { useFormatMoney } from '@/components/Money'
import { moverPercent, moverState } from './movers'

/** Highlight line + ranked list of expense-category movers (biggest absolute change first). */
export function CategoryMovers({ data, currency }: { data: TrendComparison; currency: string }) {
  const { t } = useTranslation()
  const formatMoney = useFormatMoney()

  if (data.movers.length === 0) {
    return (
      <Card className="mt-6 p-5">
        <p className="text-sm text-fg-soft">{t('trends.noMovers')}</p>
      </Card>
    )
  }

  const signed = (minor: number) =>
    `${minor > 0 ? '+' : minor < 0 ? '−' : ''}${formatMoney(Math.abs(minor), currency)}`

  // movers.length === 0 is guarded above; the non-null assertion is safe here
  // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
  const top = data.movers[0]!
  const highlight = t('trends.highlight', { name: top.name, change: signed(top.deltaMinor) })

  const changeText = (m: CategoryMover) => {
    const state = moverState(m)
    if (state === 'new') return t('trends.moverNew')
    if (state === 'gone') return t('trends.moverGone')
    if (state === 'flat') return '—'
    const pct = moverPercent(m)
    return pct !== null ? `${Math.abs(pct).toFixed(0)}%` : signed(m.deltaMinor)
  }

  return (
    <Card className="mt-6 overflow-hidden p-0">
      <p className="border-b border-border-subtle bg-surface-2/40 px-4 py-2 text-xs font-medium uppercase tracking-wide text-fg-soft">
        {t('trends.moversTitle')}
      </p>
      <p className="px-4 py-3 text-sm text-fg">{highlight}</p>
      <ul className="divide-y divide-border-subtle">
        {data.movers.map((m) => {
          const state = moverState(m)
          const worse = state === 'up' || state === 'new'
          const better = state === 'down' || state === 'gone'
          const tone = worse ? 'text-negative' : better ? 'text-positive' : 'text-fg-subtle'
          const arrow = worse ? '▲ ' : better ? '▼ ' : ''
          return (
            <li key={m.categoryId != null ? String(m.categoryId) : 'uncategorized'} className="flex items-center gap-3 px-4 py-2.5">
              <span className="h-2.5 w-2.5 flex-none rounded-sm" style={{ backgroundColor: m.color }} />
              <span className="flex-1 text-sm text-fg">{m.name}</span>
              <span className="w-24 text-right text-sm tabular-nums text-fg-soft">
                {formatMoney(m.currentMinor, currency)}
              </span>
              <span className={`w-28 text-right text-sm font-medium tabular-nums ${tone}`}>
                {arrow}
                {changeText(m)}
              </span>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}
