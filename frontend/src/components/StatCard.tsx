import type { ReactNode } from 'react'
import { Card } from '@/components/primitives'
import { Money, useFormatMoney } from '@/components/Money'

export function StatCard({
  label,
  minor,
  currency,
  tone,
  previousMinor,
  goodWhenUp,
  compareLabel,
}: {
  label: string
  minor: number
  currency: string
  tone: 'income' | 'expense' | 'net'
  // When set, show the change vs this previous-period value.
  previousMinor?: number
  goodWhenUp?: boolean
  compareLabel?: string
}) {
  const formatMoney = useFormatMoney()
  const ring = tone === 'income' ? 'ring-positive/20' : tone === 'expense' ? 'ring-negative/20' : 'ring-brand-200'
  // Colour the amount by tone (income green, expense red, net neutral) rather than by sign — expense
  // totals are positive numbers, so a sign-based colour would show them green like income.
  const amount = tone === 'income' ? 'text-positive' : tone === 'expense' ? 'text-negative' : ''

  let delta: ReactNode = null
  if (previousMinor !== undefined) {
    const d = minor - previousMinor
    // % only when there's a base to compare against; otherwise show the absolute change.
    const pct = previousMinor !== 0 ? (d / previousMinor) * 100 : null
    const good = d === 0 ? null : d > 0 === Boolean(goodWhenUp)
    const toneClass = good === null ? 'text-fg-subtle' : good ? 'text-positive' : 'text-negative'
    const arrow = d === 0 ? '' : d > 0 ? '▲ ' : '▼ '
    const change = d === 0 ? '—' : pct !== null ? `${Math.abs(pct).toFixed(1)}%` : formatMoney(Math.abs(d), currency)
    delta = (
      <p className={`mt-1 text-xs font-medium ${toneClass}`}>
        {arrow}
        {change} <span className="font-normal text-fg-subtle">{compareLabel}</span>
      </p>
    )
  }

  return (
    <Card className={`p-5 ring-1 ${ring}`}>
      <p className="text-sm text-fg-soft">{label}</p>
      <p className={`mt-2 text-2xl font-semibold ${amount}`}>
        <Money minor={minor} currency={currency} />
      </p>
      {delta}
    </Card>
  )
}
