import { type ChangeEvent, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Card } from '@/components/primitives'
import { useToast } from '@/components/Toast'
import { exportApi } from './api'
import { useRestore } from './hooks'

export function ExportCard() {
  const { t } = useTranslation()
  const toast = useToast()
  const [busy, setBusy] = useState<'csv' | 'json' | 'backup' | null>(null)
  const restore = useRestore()
  const fileInput = useRef<HTMLInputElement>(null)

  const run = async (format: 'csv' | 'json' | 'backup') => {
    setBusy(format)
    try {
      await exportApi[format]()
    } catch {
      toast.error(t('errors.generic'))
    } finally {
      setBusy(null)
    }
  }

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = '' // let the same file be picked again
    if (!file) return
    let backup: unknown
    try {
      backup = JSON.parse(await file.text())
    } catch {
      toast.error(t('export.restoreInvalid'))
      return
    }
    restore.mutate(backup, {
      onSuccess: (s) =>
        toast.success(
          t('export.restored', {
            transactions: s.transactionsImported,
            accounts: s.accountsCreated,
            categories: s.categoriesCreated,
            skipped: s.transactionsSkipped + s.transfersSkipped,
          }),
        ),
      onError: () => toast.error(t('errors.generic')),
    })
  }

  return (
    <Card className="max-w-md p-5">
      <h2 className="text-sm font-semibold text-fg-muted">{t('export.title')}</h2>
      <p className="mt-1 text-xs text-fg-soft">{t('export.hint')}</p>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button variant="secondary" loading={busy === 'csv'} onClick={() => void run('csv')}>
          {t('export.csv')}
        </Button>
        <Button variant="secondary" loading={busy === 'json'} onClick={() => void run('json')}>
          {t('export.json')}
        </Button>
        <Button variant="secondary" loading={busy === 'backup'} onClick={() => void run('backup')}>
          {t('export.backup')}
        </Button>
        <Button
          variant="secondary"
          loading={restore.isPending}
          onClick={() => fileInput.current?.click()}
        >
          {t('export.restore')}
        </Button>
      </div>
      <input
        ref={fileInput}
        type="file"
        accept="application/json,.json"
        className="hidden"
        onChange={(e) => void onFile(e)}
      />
    </Card>
  )
}
