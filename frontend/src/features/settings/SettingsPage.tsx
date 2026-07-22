import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '@/api'
import { Button, Card, CenteredState, Field, Input, PageHeader, Skeleton } from '@/components/primitives'
import { useToast } from '@/components/Toast'
import { FxRatesCard } from './FxRatesCard'
import { useSettings, useUpdateSettings } from './hooks'

export function SettingsPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, isLoading, isError, refetch } = useSettings()
  const update = useUpdateSettings()
  const [currency, setCurrency] = useState('')
  // Seed the editable field from server state on load (and after a save refetches a new value)
  // without an effect: a guarded setState during render is React's blessed way to sync to a prop.
  const [syncedCurrency, setSyncedCurrency] = useState<string>()
  if (data && data.reportingCurrency !== syncedCurrency) {
    setSyncedCurrency(data.reportingCurrency)
    setCurrency(data.reportingCurrency)
  }

  const valid = /^[A-Za-z]{3}$/.test(currency)

  const onSave = () => {
    if (!valid) return
    update.mutate(
      { reportingCurrency: currency.toUpperCase() },
      {
        onSuccess: () => toast.success(t('settings.saved')),
        onError: (err) => toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic')),
      },
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader title={t('settings.title')} />
      {isLoading ? (
        <Card className="max-w-md p-5">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="mt-3 h-9 w-full" />
        </Card>
      ) : isError ? (
        <CenteredState title={t('errors.loadFailed')} action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>} />
      ) : (
        <Card className="max-w-md p-5">
          <Field label={t('settings.reportingCurrency')} htmlFor="reporting-ccy" hint={t('settings.reportingHint')}>
            <Input
              id="reporting-ccy"
              maxLength={3}
              className="uppercase"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
            />
          </Field>
          <div className="mt-4">
            <Button onClick={onSave} loading={update.isPending} disabled={!valid}>
              {t('common.save')}
            </Button>
          </div>
        </Card>
      )}
      <FxRatesCard />
    </div>
  )
}
