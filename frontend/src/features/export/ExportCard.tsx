import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Card } from '@/components/primitives'
import { useToast } from '@/components/Toast'
import { exportApi } from './api'

export function ExportCard() {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState<'csv' | 'json' | null>(null)

  const run = async (format: 'csv' | 'json') => {
    setBusy(format)
    try {
      await (format === 'csv' ? exportApi.csv() : exportApi.json())
    } catch {
      toast.error(t('errors.generic'))
    } finally {
      setBusy(null)
    }
  }

  return (
    <Card className="max-w-md p-5">
      <h2 className="text-sm font-semibold text-slate-700">{t('export.title')}</h2>
      <p className="mt-1 text-xs text-slate-500">{t('export.hint')}</p>
      <div className="mt-4 flex gap-2">
        <Button variant="secondary" loading={busy === 'csv'} onClick={() => void run('csv')}>
          {t('export.csv')}
        </Button>
        <Button variant="secondary" loading={busy === 'json'} onClick={() => void run('json')}>
          {t('export.json')}
        </Button>
      </div>
    </Card>
  )
}
