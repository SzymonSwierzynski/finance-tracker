import { useTranslation } from 'react-i18next'
import { formatMinor } from '@/lib/money'
import type { FormatMoneyOptions } from '@/lib/money'
import { localeForLanguage } from '@/lib/i18n'

/** Locale-aware money formatter bound to the active UI language. */
// eslint-disable-next-line react-refresh/only-export-components -- hook co-located with the Money component
export function useFormatMoney() {
  const { i18n } = useTranslation()
  const locale = localeForLanguage(i18n.language)
  return (minor: number, currency = 'PLN', opts: FormatMoneyOptions = {}) =>
    formatMinor(minor, currency, { locale, ...opts })
}

interface MoneyProps {
  minor: number
  currency?: string
  /** Color negative amounts red / positive green. */
  colored?: boolean
  signDisplay?: FormatMoneyOptions['signDisplay']
  className?: string
}

/** Renders integer minor units as formatted currency (division by 100 happens only here). */
export function Money({ minor, currency = 'PLN', colored = false, signDisplay = 'auto', className = '' }: MoneyProps) {
  const format = useFormatMoney()
  const tone = colored ? (minor < 0 ? 'text-negative' : minor > 0 ? 'text-positive' : 'text-fg-soft') : ''
  return (
    <span className={`tabular-nums ${tone} ${className}`} title={`${minor} minor units`}>
      {format(minor, currency, { signDisplay })}
    </span>
  )
}
