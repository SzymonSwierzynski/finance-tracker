import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Rule } from '@/api'
import { Badge, Button, Card, CenteredState, PageHeader, Skeleton } from '@/components/primitives'
import { useToast } from '@/components/Toast'
import { useCategories } from '@/features/categories/hooks'
import { RuleForm } from './RuleForm'
import { useApplyRules, useDeleteRule, useRules } from './hooks'

export function RulesPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Rule | undefined>(undefined)
  const { data: rules, isLoading, isError, refetch } = useRules()
  const { data: categories } = useCategories()
  const del = useDeleteRule()
  const apply = useApplyRules()

  const categoryName = (id: number) => categories?.find((c) => c.id === id)?.name ?? '—'

  const openCreate = () => {
    setEditing(undefined)
    setFormOpen(true)
  }
  const openEdit = (rule: Rule) => {
    setEditing(rule)
    setFormOpen(true)
  }
  const onDelete = (rule: Rule) => {
    if (!window.confirm(t('rules.deleteConfirm'))) return
    del.mutate(rule.id, { onError: () => toast.error(t('errors.generic')) })
  }
  const onApply = () => {
    apply.mutate(undefined, {
      onSuccess: (res) =>
        toast.success(t('rules.applied', { categorized: res.categorized, scanned: res.scanned })),
      onError: () => toast.error(t('errors.generic')),
    })
  }

  return (
    <>
      <PageHeader
        title={t('rules.title')}
        subtitle={t('rules.subtitle')}
        actions={
          <>
            <Button
              variant="secondary"
              onClick={onApply}
              loading={apply.isPending}
              disabled={!rules?.length}
            >
              {t('rules.apply')}
            </Button>
            <Button onClick={openCreate}>{t('rules.new')}</Button>
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
      ) : !rules?.length ? (
        <CenteredState
          title={t('rules.empty')}
          action={<Button onClick={openCreate}>{t('rules.new')}</Button>}
        />
      ) : (
        <Card className="divide-y divide-border-subtle px-5">
          {rules.map((rule) => (
            <div key={rule.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-fg">&ldquo;{rule.pattern}&rdquo;</p>
                <p className="text-xs text-fg-soft">→ {categoryName(rule.categoryId)}</p>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Badge tone="slate">
                  {t('rules.priority')}: {rule.priority}
                </Badge>
                <Button variant="ghost" size="sm" onClick={() => openEdit(rule)}>
                  {t('common.edit')}
                </Button>
                <Button variant="ghost" size="sm" onClick={() => onDelete(rule)}>
                  {t('common.delete')}
                </Button>
              </div>
            </div>
          ))}
        </Card>
      )}

      {formOpen && <RuleForm open={formOpen} onClose={() => setFormOpen(false)} rule={editing} />}
    </>
  )
}
