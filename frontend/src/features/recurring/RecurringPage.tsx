import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Recurring } from '@/api'
import { Badge, Button, Card, CenteredState, PageHeader, Skeleton } from '@/components/primitives'
import { Money } from '@/components/Money'
import { useToast } from '@/components/Toast'
import { useCategories } from '@/features/categories/hooks'
import { formatDate } from '@/lib/date'
import { localeForLanguage } from '@/lib/i18n'
import { RecurringForm } from './RecurringForm'
import { useDeleteRecurring, useRecurring, useRunRecurring, useUpdateRecurring } from './hooks'

export function RecurringPage() {
  const { t, i18n } = useTranslation()
  const locale = localeForLanguage(i18n.language)
  const toast = useToast()
  const [formOpen, setFormOpen] = useState(false)
  const { data, isLoading, isError, refetch } = useRecurring()
  const { data: categories } = useCategories()
  const del = useDeleteRecurring()
  const update = useUpdateRecurring()
  const run = useRunRecurring()

  const categoryName = (id: number | null) =>
    id == null ? null : (categories?.find((c) => c.id === id)?.name ?? null)

  const onToggle = (r: Recurring) =>
    update.mutate(
      { id: r.id, body: { version: r.version, active: !r.active } },
      { onError: () => toast.error(t('errors.generic')) },
    )
  const onDelete = (r: Recurring) => {
    if (!window.confirm(t('recurring.deleteConfirm'))) return
    del.mutate(r.id, { onError: () => toast.error(t('errors.generic')) })
  }
  const onRun = () =>
    run.mutate(undefined, {
      onSuccess: (res) => toast.success(t('recurring.ran', { count: res.materialized })),
      onError: () => toast.error(t('errors.generic')),
    })

  return (
    <>
      <PageHeader
        title={t('recurring.title')}
        subtitle={t('recurring.subtitle')}
        actions={
          <>
            <Button variant="secondary" onClick={onRun} loading={run.isPending} disabled={!data?.length}>
              {t('recurring.run')}
            </Button>
            <Button onClick={() => setFormOpen(true)}>{t('recurring.new')}</Button>
          </>
        }
      />

      {isLoading ? (
        <Card className="p-5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="my-2 h-5 w-64" />
          ))}
        </Card>
      ) : isError ? (
        <CenteredState
          title={t('errors.loadFailed')}
          action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>}
        />
      ) : !data?.length ? (
        <CenteredState
          title={t('recurring.empty')}
          action={<Button onClick={() => setFormOpen(true)}>{t('recurring.new')}</Button>}
        />
      ) : (
        <Card className="divide-y divide-border-subtle px-5">
          {data.map((r) => (
            <div key={r.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-fg">{r.description || '—'}</p>
                <p className="text-xs text-fg-soft">
                  {t(`recurring.${r.frequency}`)}
                  {r.intervalCount > 1 ? ` ×${r.intervalCount}` : ''} · {t('recurring.next')}:{' '}
                  {formatDate(r.nextRunDate, locale)}
                  {categoryName(r.categoryId) ? ` · ${categoryName(r.categoryId)}` : ''}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Money
                  minor={r.type === 'expense' ? -r.amountMinor : r.amountMinor}
                  currency={r.currency}
                  colored
                />
                {!r.active && <Badge tone="slate">{t('recurring.paused')}</Badge>}
                <Button variant="ghost" size="sm" onClick={() => onToggle(r)}>
                  {r.active ? t('recurring.pause') : t('recurring.resume')}
                </Button>
                <Button variant="ghost" size="sm" onClick={() => onDelete(r)}>
                  {t('common.delete')}
                </Button>
              </div>
            </div>
          ))}
        </Card>
      )}

      {formOpen && <RecurringForm open={formOpen} onClose={() => setFormOpen(false)} />}
    </>
  )
}
