import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { BudgetProgress } from '@/api'
import { Button, Card, CenteredState, PageHeader, Skeleton } from '@/components/primitives'
import { Money } from '@/components/Money'
import { useToast } from '@/components/Toast'
import { todayIso } from '@/lib/date'
import { BudgetForm } from './BudgetForm'
import { useBudgets, useDeleteBudget } from './hooks'

export function BudgetsPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [month, setMonth] = useState(todayIso().slice(0, 7))
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<BudgetProgress | null>(null)
  const { data, isLoading, isError, refetch } = useBudgets(month)
  const del = useDeleteBudget()

  const items = data?.items ?? []
  const currency = data?.currency ?? 'PLN'
  const budgetedCategoryIds = items.map((b) => b.categoryId)

  const openCreate = () => {
    setEditing(null)
    setFormOpen(true)
  }
  const openEdit = (b: BudgetProgress) => {
    setEditing(b)
    setFormOpen(true)
  }
  const onDelete = (b: BudgetProgress) => {
    if (!window.confirm(t('budgets.deleteConfirm'))) return
    del.mutate(b.id, { onError: () => toast.error(t('errors.generic')) })
  }

  return (
    <>
      <PageHeader
        title={t('budgets.title')}
        subtitle={t('budgets.subtitle')}
        actions={
          <>
            <input
              type="month"
              value={month}
              onChange={(e) => setMonth(e.target.value)}
              aria-label={t('budgets.month')}
              className="rounded-lg border border-border px-3 py-2 text-sm text-fg-muted"
            />
            <Button onClick={openCreate}>{t('budgets.new')}</Button>
          </>
        }
      />

      {isLoading ? (
        <Card className="p-5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="my-2 h-12 w-full" />
          ))}
        </Card>
      ) : isError ? (
        <CenteredState
          title={t('errors.loadFailed')}
          action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>}
        />
      ) : !items.length ? (
        <CenteredState
          title={t('budgets.empty')}
          action={<Button onClick={openCreate}>{t('budgets.new')}</Button>}
        />
      ) : (
        <div className="space-y-3">
          {items.map((b) => {
            const carried = b.rollover ? b.carriedInMinor : 0
            const available = b.amountMinor + carried
            const pct =
              available > 0 ? Math.min(100, Math.round((b.spentMinor / available) * 100)) : 0
            return (
              <Card key={b.id} className="p-4">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <span
                      className="h-3 w-3 shrink-0 rounded-full"
                      style={{ backgroundColor: b.color }}
                    />
                    <span className="truncate text-sm font-medium text-fg">
                      {b.categoryName}
                    </span>
                    {b.rollover && (
                      <span
                        className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-[10px] font-medium text-fg-muted"
                        title={t('budgets.rolloverHelp')}
                      >
                        {t('budgets.rolloverBadge')}
                      </span>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <Button variant="ghost" size="sm" onClick={() => openEdit(b)}>
                      {t('common.edit')}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => onDelete(b)}>
                      {t('common.delete')}
                    </Button>
                  </div>
                </div>
                <div
                  className="mt-3 h-2 w-full overflow-hidden rounded-full bg-surface-2"
                  role="progressbar"
                  aria-valuenow={pct}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <div
                    className="h-full rounded-full transition-[width] duration-700 ease-out"
                    style={
                      b.over
                        ? { width: '100%', background: '#ef4444' }
                        : {
                            // Gradient anchored to the FULL track (background-size = track/fill), so the
                            // fill's leading edge shows the "health" colour at the current spend %.
                            width: `${pct}%`,
                            backgroundImage:
                              'linear-gradient(90deg, #10b981 0%, #eab308 55%, #f97316 80%, #ef4444 100%)',
                            backgroundSize: `${pct > 0 ? (100 / pct) * 100 : 100}% 100%`,
                            backgroundRepeat: 'no-repeat',
                          }
                    }
                  />
                </div>
                <div className="mt-2 flex items-center justify-between text-xs text-fg-soft">
                  <span>
                    <Money minor={b.spentMinor} currency={currency} /> /{' '}
                    <Money minor={available} currency={currency} />
                    {carried > 0 && (
                      <span className="ml-1 text-positive">
                        (+<Money minor={carried} currency={currency} /> {t('budgets.carried')})
                      </span>
                    )}
                  </span>
                  <span className={b.over ? 'font-medium text-negative' : 'text-fg-muted'}>
                    {b.over ? t('budgets.over') : t('budgets.left')}:{' '}
                    <Money minor={Math.abs(b.remainingMinor)} currency={currency} />
                  </span>
                </div>
              </Card>
            )
          })}
        </div>
      )}

      {formOpen && (
        <BudgetForm
          open={formOpen}
          onClose={() => setFormOpen(false)}
          edit={editing ?? undefined}
          budgetedCategoryIds={budgetedCategoryIds}
        />
      )}
    </>
  )
}
