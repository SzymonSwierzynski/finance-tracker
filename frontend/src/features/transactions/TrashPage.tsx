import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Transaction } from '@/api'
import { Button, Card, CenteredState, PageHeader, Skeleton } from '@/components/primitives'
import { Money } from '@/components/Money'
import { useToast } from '@/components/Toast'
import { usePermanentlyDeleteTransaction, useRestoreTransaction, useTrash } from './hooks'

export function TrashPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const { data, isLoading, isError, refetch } = useTrash()
  const restore = useRestoreTransaction()
  const permanent = usePermanentlyDeleteTransaction()

  const items = data?.items ?? []

  const onRestore = (tx: Transaction) =>
    restore.mutate(tx.id, {
      onSuccess: () => toast.success(t('transactions.restored')),
      onError: () => toast.error(t('errors.generic')),
    })
  const onPermanent = (tx: Transaction) => {
    if (!window.confirm(t('transactions.deleteForeverConfirm'))) return
    permanent.mutate(tx.id, { onError: () => toast.error(t('errors.generic')) })
  }

  return (
    <>
      <PageHeader
        title={t('transactions.trashTitle')}
        actions={
          <Link
            to="/transactions"
            className="rounded-lg px-3 py-2 text-sm font-medium text-fg-muted hover:text-fg"
          >
            {t('transactions.title')}
          </Link>
        }
      />
      {isLoading ? (
        <Card className="p-5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="my-2 h-10 w-full" />
          ))}
        </Card>
      ) : isError ? (
        <CenteredState
          title={t('errors.loadFailed')}
          action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>}
        />
      ) : !items.length ? (
        <CenteredState title={t('transactions.trashEmpty')} />
      ) : (
        <div className="space-y-2">
          {items.map((tx) => (
            <Card key={tx.id} className="flex items-center justify-between gap-3 p-3">
              <div className="min-w-0 text-sm">
                <span className="text-fg-muted">{tx.date}</span>{' '}
                <span className="font-medium text-fg">{tx.description || '—'}</span>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Money minor={tx.amountMinor} currency={tx.currency} />
                <Button variant="ghost" size="sm" onClick={() => onRestore(tx)}>
                  {t('transactions.restore')}
                </Button>
                <Button variant="ghost" size="sm" onClick={() => onPermanent(tx)}>
                  {t('transactions.deleteForever')}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </>
  )
}
