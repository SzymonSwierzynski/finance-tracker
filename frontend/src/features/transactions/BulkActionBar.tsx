import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Select } from '@/components/primitives'

export function BulkActionBar({
  count,
  categories,
  canCategorize,
  onClear,
  onDelete,
  onCategorize,
}: {
  count: number
  categories: { id: number; name: string; parentId: number | null }[]
  canCategorize: boolean
  onClear: () => void
  onDelete: () => void
  onCategorize: (categoryId: number | null) => void
}) {
  const { t } = useTranslation()
  const [choice, setChoice] = useState('')

  const apply = () => {
    if (!choice) return
    onCategorize(choice === 'none' ? null : Number(choice))
    setChoice('')
  }

  return (
    <div className="mb-4 flex flex-wrap items-center gap-3 rounded-lg border border-border bg-surface-2 px-4 py-2.5 text-sm">
      <span className="font-medium text-fg">{t('transactions.selectedCount', { count })}</span>
      <div className="ml-auto flex flex-wrap items-center gap-2">
        <Select
          aria-label={t('transactions.recategorize')}
          value={choice}
          disabled={!canCategorize}
          title={canCategorize ? undefined : t('transactions.recategorizeHint')}
          onChange={(e) => setChoice(e.target.value)}
        >
          <option value="">{t('transactions.recategorize')}…</option>
          <option value="none">{t('transactions.uncategorize')}</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.parentId == null ? c.name : `· ${c.name}`}
            </option>
          ))}
        </Select>
        <Button variant="secondary" size="sm" disabled={!canCategorize || !choice} onClick={apply}>
          {t('common.save')}
        </Button>
        <Button variant="ghost" size="sm" onClick={onDelete}>
          {t('common.delete')}
        </Button>
        <Button variant="ghost" size="sm" onClick={onClear}>
          {t('common.cancel')}
        </Button>
      </div>
    </div>
  )
}
