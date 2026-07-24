import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api'
import type { FxRate } from '@/api'
import { Badge, Button, Card, Field, Input, Skeleton } from '@/components/primitives'
import { useToast } from '@/components/Toast'
import { useDeleteFxRate, useFxRates, useUpsertFxRate } from './fxHooks'

/**
 * Rates are decimals, not money, so they do NOT go through the minor-units parser. They are still
 * typed by Polish users though, so a decimal comma has to work.
 */
function parseRate(input: string): number | null {
  const normalized = input.trim().replace(/\s/g, '').replace(',', '.')
  if (!/^\d*\.?\d+$/.test(normalized)) return null
  const value = Number(normalized)
  return Number.isFinite(value) && value > 0 ? value : null
}

export function FxRatesCard() {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, isLoading, isError, refetch } = useFxRates()
  const upsert = useUpsertFxRate()
  const remove = useDeleteFxRate()

  const [currency, setCurrency] = useState('')
  const [rate, setRate] = useState('')

  const base = data?.baseCurrency ?? ''
  const parsedRate = parseRate(rate)
  const currencyValid = /^[A-Za-z]{3}$/.test(currency)
  const isBase = currencyValid && currency.toUpperCase() === base
  const canSubmit = currencyValid && !isBase && parsedRate !== null

  const showError = (err: unknown) =>
    toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))

  const onAdd = () => {
    if (!canSubmit || parsedRate === null) return
    upsert.mutate(
      { currency, body: { rateToBase: parsedRate } },
      {
        onSuccess: () => {
          toast.success(t('fx.saved'))
          setCurrency('')
          setRate('')
        },
        onError: showError,
      },
    )
  }

  const onRemove = (fxRate: FxRate) => {
    if (!window.confirm(t('fx.removeConfirm', { currency: fxRate.currency }))) return
    remove.mutate(fxRate.currency, {
      onSuccess: () => toast.success(t('fx.deleted')),
      onError: showError,
    })
  }

  return (
    <Card className="max-w-md p-5">
      <h2 className="text-base font-semibold text-fg">{t('fx.title')}</h2>
      <p className="mt-1 text-xs text-fg-soft">{t('fx.hint')}</p>

      {isLoading ? (
        <div className="mt-4 space-y-2">
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
        </div>
      ) : isError ? (
        <div className="mt-4 flex items-center justify-between gap-3 rounded-lg bg-surface-2 p-3">
          <p className="text-sm text-fg-muted">{t('errors.loadFailed')}</p>
          <Button size="sm" variant="secondary" onClick={() => void refetch()}>
            {t('common.retry')}
          </Button>
        </div>
      ) : data && data.rates.length === 0 ? (
        <p className="mt-4 rounded-lg bg-surface-2 p-3 text-sm text-fg-soft">{t('fx.empty')}</p>
      ) : (
        <ul className="mt-4 divide-y divide-border-subtle">
          {data?.rates.map((fxRate) => (
            <li key={fxRate.currency} className="flex items-start justify-between gap-3 py-2.5">
              <div className="min-w-0">
                <p className="text-sm text-fg">
                  {t('fx.example', {
                    currency: fxRate.currency,
                    rate: fxRate.rateToBase,
                    base: fxRate.baseCurrency,
                  })}
                </p>
                {fxRate.stale && (
                  <>
                    <Badge tone="red">{t('fx.stale', { base: fxRate.baseCurrency })}</Badge>
                    <p className="mt-1 text-xs text-fg-soft">
                      {t('fx.staleHint', { base: fxRate.baseCurrency })}
                    </p>
                  </>
                )}
              </div>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => onRemove(fxRate)}
                aria-label={`${t('common.delete')} ${fxRate.currency}`}
              >
                {t('common.delete')}
              </Button>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-4 grid grid-cols-[6rem_1fr] gap-3">
        <Field
          label={t('fx.currency')}
          htmlFor="fx-currency"
          error={isBase ? t('fx.sameAsBase') : undefined}
        >
          <Input
            id="fx-currency"
            maxLength={3}
            className="uppercase"
            placeholder="EUR"
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
          />
        </Field>
        <Field label={t('fx.rate')} htmlFor="fx-rate" hint={base ? `→ ${base}` : undefined}>
          <Input
            id="fx-rate"
            inputMode="decimal"
            placeholder="4.30"
            value={rate}
            onChange={(e) => setRate(e.target.value)}
          />
        </Field>
      </div>
      <div className="mt-3">
        <Button size="sm" onClick={onAdd} loading={upsert.isPending} disabled={!canSubmit}>
          {t('fx.addRate')}
        </Button>
      </div>
    </Card>
  )
}
