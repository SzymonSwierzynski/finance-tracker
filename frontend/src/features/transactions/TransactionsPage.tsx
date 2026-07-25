import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import type { Transaction, TransactionType } from '@/api'
import { TRANSACTION_TYPES } from '@/api'
import { Badge, Button, Card, CenteredState, PageHeader, Select, Input, Skeleton } from '@/components/primitives'
import { Money } from '@/components/Money'
import { useToast } from '@/components/Toast'
import { formatDate } from '@/lib/date'
import { localeForLanguage } from '@/lib/i18n'
import { useAccounts } from '@/features/accounts/hooks'
import { useCategories } from '@/features/categories/hooks'
import { useSettings } from '@/features/settings/hooks'
import { TransactionForm } from './TransactionForm'
import { useDeleteTransaction, useTransactions } from './hooks'

const PAGE_SIZE = 25

export function TransactionsPage() {
  const { t, i18n } = useTranslation()
  const toast = useToast()
  const locale = localeForLanguage(i18n.language)

  const [searchParams] = useSearchParams()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [accountId, setAccountId] = useState('')
  const [type, setType] = useState('')
  const [categoryId, setCategoryId] = useState(searchParams.get('categoryId') ?? '')
  const [q, setQ] = useState('')
  const [sort, setSort] = useState('')
  const [page, setPage] = useState(0)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Transaction | undefined>(undefined)

  const filters = {
    from: from || undefined,
    to: to || undefined,
    accountId: accountId ? Number(accountId) : undefined,
    type: (type || undefined) as TransactionType | undefined,
    categoryId: categoryId ? Number(categoryId) : undefined,
    q: q || undefined,
    sort: sort || undefined,
    page,
    size: PAGE_SIZE,
  }

  const effectiveSort = sort || 'date,desc'
  const [sortField, sortDir] = effectiveSort.split(',')
  const toggleSort = (field: 'date' | 'amount') => {
    setPage(0)
    setSort((prev) => {
      const [f, d] = (prev || 'date,desc').split(',')
      return f === field ? `${field},${d === 'asc' ? 'desc' : 'asc'}` : `${field},desc`
    })
  }
  const sortArrow = (field: string) => (sortField === field ? (sortDir === 'asc' ? '↑' : '↓') : '')

  const { data, isLoading, isError, refetch } = useTransactions(filters)
  const { data: accounts } = useAccounts(true)
  const { data: categories } = useCategories()
  const { data: settings } = useSettings()
  const baseCurrency = settings?.reportingCurrency ?? 'PLN'

  const accountName = useMemo(() => {
    const map = new Map<number, string>()
    for (const a of accounts ?? []) map.set(a.id, a.name)
    return (id: number | null) => (id == null ? '—' : (map.get(id) ?? `#${id}`))
  }, [accounts])

  const categoryName = useMemo(() => {
    const map = new Map<number, string>()
    for (const c of categories ?? []) map.set(c.id, c.name)
    return (id: number | null) => (id == null ? null : (map.get(id) ?? null))
  }, [categories])

  const remove = useDeleteTransaction()
  const onDelete = (tx: Transaction) => {
    if (!window.confirm(t('transactions.deleteConfirm'))) return
    remove.mutate(tx.id, {
      onSuccess: () => toast.success('✓'),
      onError: () => toast.error(t('errors.generic')),
    })
  }

  const total = data?.total ?? 0
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <>
      <PageHeader
        title={t('transactions.title')}
        actions={
          <Button
            onClick={() => {
              setEditing(undefined)
              setFormOpen(true)
            }}
          >
            {t('transactions.new')}
          </Button>
        }
      />

      <Card className="mb-4 p-4">
        <Input
          type="search"
          aria-label={t('transactions.search')}
          placeholder={t('transactions.search')}
          value={q}
          onChange={(e) => {
            setPage(0)
            setQ(e.target.value)
          }}
          className="mb-3"
        />
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Input type="date" aria-label={t('transactions.from')} value={from} onChange={(e) => { setPage(0); setFrom(e.target.value) }} />
          <Input type="date" aria-label={t('transactions.to')} value={to} onChange={(e) => { setPage(0); setTo(e.target.value) }} />
          <Select aria-label={t('transactions.account')} value={accountId} onChange={(e) => { setPage(0); setAccountId(e.target.value) }}>
            <option value="">{t('common.all')} — {t('transactions.account')}</option>
            {(accounts ?? []).map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </Select>
          <Select aria-label={t('transactions.type')} value={type} onChange={(e) => { setPage(0); setType(e.target.value) }}>
            <option value="">{t('common.all')} — {t('transactions.type')}</option>
            {TRANSACTION_TYPES.map((tt) => (
              <option key={tt} value={tt}>
                {t(`transactions.${tt}`)}
              </option>
            ))}
          </Select>
          <Select aria-label={t('transactions.category')} value={categoryId} onChange={(e) => { setPage(0); setCategoryId(e.target.value) }}>
            <option value="">{t('common.all')} — {t('transactions.category')}</option>
            {(categories ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.parentId == null ? c.name : `· ${c.name}`}
              </option>
            ))}
          </Select>
        </div>
      </Card>

      {isLoading ? (
        <Card className="divide-y divide-border-subtle">
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i} className="flex items-center justify-between p-4">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </Card>
      ) : isError ? (
        <CenteredState title={t('errors.loadFailed')} action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>} />
      ) : !data || data.items.length === 0 ? (
        <CenteredState title={t('transactions.empty')} />
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-fg-soft">
                  <th className="px-4 py-3 font-medium">
                    <button
                      className="inline-flex items-center gap-1 hover:text-fg-muted"
                      onClick={() => toggleSort('date')}
                    >
                      {t('transactions.date')} <span className="text-fg-subtle">{sortArrow('date')}</span>
                    </button>
                  </th>
                  <th className="px-4 py-3 font-medium">{t('transactions.description')}</th>
                  <th className="px-4 py-3 font-medium">{t('transactions.account')}</th>
                  <th className="px-4 py-3 text-right font-medium">
                    <button
                      className="ml-auto inline-flex items-center gap-1 hover:text-fg-muted"
                      onClick={() => toggleSort('amount')}
                    >
                      {t('transactions.amount')} <span className="text-fg-subtle">{sortArrow('amount')}</span>
                    </button>
                  </th>
                  <th className="px-4 py-3 text-right font-medium">{t('transactions.baseValue')}</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle">
                {data.items.map((tx) => (
                  <tr key={tx.id} className="hover:bg-surface-2">
                    <td className="whitespace-nowrap px-4 py-3 text-fg-soft">{formatDate(tx.date, locale)}</td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-fg">{tx.description || '—'}</div>
                      {tx.type === 'transfer' ? (
                        <div className="text-xs text-fg-subtle">→ {accountName(tx.counterAccountId)}</div>
                      ) : (
                        categoryName(tx.categoryId) && (
                          <div className="text-xs text-fg-subtle">{categoryName(tx.categoryId)}</div>
                        )
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <Badge tone={tx.type === 'income' ? 'green' : tx.type === 'expense' ? 'red' : 'slate'}>
                        {t(`transactions.${tx.type}`)}
                      </Badge>
                      <span className="ml-2 text-fg-soft">{accountName(tx.accountId)}</span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right font-medium">
                      {tx.type === 'transfer' ? (
                        <Money minor={tx.amountMinor} currency={tx.currency} />
                      ) : (
                        <Money
                          minor={tx.type === 'expense' ? -tx.amountMinor : tx.amountMinor}
                          currency={tx.currency}
                          colored
                          signDisplay="always"
                        />
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right text-fg-soft">
                      <Money minor={tx.baseMinor} currency={baseCurrency} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setEditing(tx)
                            setFormOpen(true)
                          }}
                        >
                          {t('common.edit')}
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => onDelete(tx)}>
                          {t('common.delete')}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between border-t border-border px-4 py-3 text-sm text-fg-soft">
            <span>{total}</span>
            <div className="flex items-center gap-2">
              <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                ←
              </Button>
              <span>
                {page + 1} / {pageCount}
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= pageCount} onClick={() => setPage((p) => p + 1)}>
                →
              </Button>
            </div>
          </div>
        </Card>
      )}

      {formOpen && <TransactionForm open={formOpen} onClose={() => setFormOpen(false)} transaction={editing} />}
    </>
  )
}
