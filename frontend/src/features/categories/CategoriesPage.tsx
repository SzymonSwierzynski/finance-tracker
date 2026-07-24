import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { Category, CategoryKind } from '@/api'
import { CATEGORY_KINDS } from '@/api'
import { Button, Card, CenteredState, PageHeader, Skeleton } from '@/components/primitives'
import { useToast } from '@/components/Toast'
import { CategoryForm } from './CategoryForm'
import { useCategories, useDeleteCategory } from './hooks'

function Swatch({ color }: { color: string }) {
  return <span className="inline-block size-3 shrink-0 rounded-full" style={{ backgroundColor: color }} />
}

function Chevron({ open }: { open: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={`size-3.5 transition-transform duration-200 ${open ? 'rotate-90' : ''}`}
    >
      <path d="M9 6l6 6-6 6" />
    </svg>
  )
}

function Row({
  category,
  child,
  leading,
  onEdit,
  onDelete,
}: {
  category: Category
  child?: boolean
  leading?: ReactNode
  onEdit: (c: Category) => void
  onDelete: (c: Category) => void
}) {
  const { t } = useTranslation()
  return (
    <div className={`flex items-center justify-between py-2.5 ${child ? 'pl-8' : ''}`}>
      <span className="flex items-center gap-2 text-sm text-fg">
        {leading}
        <Swatch color={category.color} />
        {category.name}
      </span>
      <span className="flex gap-1">
        <Button variant="ghost" size="sm" onClick={() => onEdit(category)}>
          {t('common.edit')}
        </Button>
        <Button variant="ghost" size="sm" onClick={() => onDelete(category)}>
          {t('common.delete')}
        </Button>
      </span>
    </div>
  )
}

export function CategoriesPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [kind, setKind] = useState<CategoryKind>('expense')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Category | undefined>(undefined)
  // Which parent ids are collapsed (empty = everything expanded, i.e. the previous behaviour).
  const [collapsed, setCollapsed] = useState<Set<number>>(() => new Set())
  const { data, isLoading, isError, refetch } = useCategories(kind)
  const del = useDeleteCategory()

  const grouped = useMemo(() => {
    const all = data ?? []
    const tops = all.filter((c) => c.parentId == null)
    const childrenOf = (id: number) => all.filter((c) => c.parentId === id)
    return tops.map((top) => ({ top, children: childrenOf(top.id) }))
  }, [data])

  const parentsWithChildren = grouped.filter((g) => g.children.length > 0).map((g) => g.top.id)
  const allCollapsed =
    parentsWithChildren.length > 0 && parentsWithChildren.every((id) => collapsed.has(id))

  const toggle = (id: number) =>
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  const toggleAll = () => setCollapsed(allCollapsed ? new Set() : new Set(parentsWithChildren))

  const openCreate = () => {
    setEditing(undefined)
    setFormOpen(true)
  }
  const openEdit = (c: Category) => {
    setEditing(c)
    setFormOpen(true)
  }
  const onDelete = (c: Category) => {
    if (!window.confirm(t('categories.deleteConfirm'))) return
    del.mutate(c.id, {
      onSuccess: (res) => toast.success(t('categories.deleted', { count: res.uncategorizedTransactions })),
      onError: () => toast.error(t('errors.generic')),
    })
  }

  return (
    <>
      <PageHeader
        title={t('categories.title')}
        actions={
          <>
            {parentsWithChildren.length > 0 && (
              <Button variant="secondary" onClick={toggleAll}>
                {allCollapsed ? t('categories.expandAll') : t('categories.collapseAll')}
              </Button>
            )}
            <div className="flex rounded-lg bg-surface-2 p-0.5">
              {CATEGORY_KINDS.map((k) => (
                <button
                  key={k}
                  onClick={() => setKind(k)}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium ${kind === k ? 'bg-surface text-fg shadow-sm' : 'text-fg-soft'}`}
                >
                  {t(`categories.${k}`)}
                </button>
              ))}
            </div>
            <Button onClick={openCreate}>{t('categories.new')}</Button>
          </>
        }
      />

      {isLoading ? (
        <Card className="p-5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="my-2 h-5 w-48" />
          ))}
        </Card>
      ) : isError ? (
        <CenteredState title={t('errors.loadFailed')} action={<Button onClick={() => void refetch()}>{t('common.retry')}</Button>} />
      ) : grouped.length === 0 ? (
        <CenteredState title={t('categories.empty')} action={<Button onClick={openCreate}>{t('categories.new')}</Button>} />
      ) : (
        <Card className="divide-y divide-border-subtle px-5">
          {grouped.map(({ top, children }) => {
            const hasChildren = children.length > 0
            const isOpen = !collapsed.has(top.id)
            return (
              <div key={top.id} className="py-1">
                <Row
                  category={top}
                  onEdit={openEdit}
                  onDelete={onDelete}
                  leading={
                    hasChildren ? (
                      <button
                        onClick={() => toggle(top.id)}
                        aria-expanded={isOpen}
                        aria-label={`${isOpen ? t('categories.collapse') : t('categories.expand')} ${top.name}`}
                        className="-ml-1 flex size-5 items-center justify-center rounded text-fg-subtle hover:bg-surface-2 hover:text-fg"
                      >
                        <Chevron open={isOpen} />
                      </button>
                    ) : (
                      <span className="inline-block size-5" aria-hidden="true" />
                    )
                  }
                />
                {hasChildren && (
                  <div
                    className="grid transition-[grid-template-rows] duration-200 ease-out"
                    style={{ gridTemplateRows: isOpen ? '1fr' : '0fr' }}
                  >
                    <div className="overflow-hidden">
                      {children.map((c) => (
                        <Row key={c.id} category={c} child onEdit={openEdit} onDelete={onDelete} />
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </Card>
      )}

      {formOpen && (
        <CategoryForm open={formOpen} onClose={() => setFormOpen(false)} category={editing} defaultKind={kind} />
      )}
    </>
  )
}
