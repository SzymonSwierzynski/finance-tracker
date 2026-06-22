import { formatMinor } from '@/lib/money'
import type { FormatMoneyOptions } from '@/lib/money'

interface MoneyProps extends FormatMoneyOptions {
  /** Integer minor units. */
  minor: number
  currency?: string
  className?: string
  /** Colour green when positive, red when negative. */
  colorBySign?: boolean
}

/** Render integer minor units as formatted money. */
export function Money({
  minor,
  currency = 'PLN',
  colorBySign = false,
  className = '',
  ...opts
}: MoneyProps) {
  const text = formatMinor(minor, currency, opts)
  const color = colorBySign
    ? minor < 0
      ? 'text-red-600'
      : minor > 0
        ? 'text-emerald-600'
        : 'text-slate-500'
    : ''
  return <span className={`tabular-nums ${color} ${className}`.trim()}>{text}</span>
}
