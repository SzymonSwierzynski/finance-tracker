import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Account } from '@/api'
import { Badge, Button, Card, CenteredState, PageHeader, Skeleton } from '@/components/primitives'
import { Money } from '@/components/Money'
import { useToast } from '@/components/Toast'
import { AccountForm } from './AccountForm'
import { useAccountBalance, useAccounts, useArchiveAccount } from './hooks'

function BalanceRow({ account }: { account: Account }) {
  const { t } = useTranslation()
  const { data, isLoading } = useAccountBalance(account.id, account.trackBalance)
  if (!account.trackBalance) return null
  return (
    <div className="mt-3 flex items-baseline justify-between border-t border-border-subtle pt-3">
      <span className="text-xs text-fg-soft">{t('accounts.balance')}</span>
      {isLoading || !data ? (
        <Skeleton className="h-5 w-20" />
      ) : (
        <Money minor={data.balanceMinor} currency={account.currency} colored className="text-sm font-semibold" />
      )}
    </div>
  )
}

function AccountCard({ account, onEdit }: { account: Account; onEdit: (a: Account) => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const archive = useArchiveAccount()

  const onArchive = () => {
    if (!window.confirm(t('accounts.archiveConfirm'))) return
    archive.mutate(account.id, {
      onSuccess: () => toast.success(`${account.name} ✓`),
      onError: () => toast.error(t('errors.generic')),
    })
  }

  return (
    <Card className="p-5">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-semibold text-fg">{account.name}</p>
          <div className="mt-1 flex items-center gap-2">
            <Badge tone="indigo">{t(`accounts.${account.type}`)}</Badge>
            <span className="text-xs text-fg-soft">{account.currency}</span>
            {account.archived && <Badge>{t('accounts.archived')}</Badge>}
          </div>
        </div>
      </div>
      <BalanceRow account={account} />
      <div className="mt-4 flex gap-2">
        <Button variant="secondary" size="sm" onClick={() => onEdit(account)}>
          {t('common.edit')}
        </Button>
        {!account.archived && (
          <Button variant="ghost" size="sm" onClick={onArchive} loading={archive.isPending}>
            {t('common.archive')}
          </Button>
        )}
      </div>
    </Card>
  )
}

export function AccountsPage() {
  const { t } = useTranslation()
  const [showArchived, setShowArchived] = useState(false)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Account | undefined>(undefined)
  const { data, isLoading, isError, refetch } = useAccounts(showArchived)

  const openCreate = () => {
    setEditing(undefined)
    setFormOpen(true)
  }
  const openEdit = (a: Account) => {
    setEditing(a)
    setFormOpen(true)
  }

  return (
    <>
      <PageHeader
        title={t('accounts.title')}
        actions={
          <>
            <label className="flex items-center gap-2 text-sm text-fg-muted">
              <input
                type="checkbox"
                className="size-4 rounded border-border-strong text-accent"
                checked={showArchived}
                onChange={(e) => setShowArchived(e.target.checked)}
              />
              {t('accounts.showArchived')}
            </label>
            <Button onClick={openCreate}>{t('accounts.new')}</Button>
          </>
        }
      />

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Card key={i} className="p-5">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="mt-3 h-4 w-20" />
              <Skeleton className="mt-6 h-8 w-24" />
            </Card>
          ))}
        </div>
      ) : isError ? (
        <CenteredState
          title={t('errors.loadFailed')}
          action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>}
        />
      ) : !data || data.length === 0 ? (
        <CenteredState title={t('accounts.empty')} action={<Button onClick={openCreate}>{t('accounts.new')}</Button>} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((account) => (
            <AccountCard key={account.id} account={account} onEdit={openEdit} />
          ))}
        </div>
      )}

      <AccountForm open={formOpen} onClose={() => setFormOpen(false)} account={editing} />
    </>
  )
}
