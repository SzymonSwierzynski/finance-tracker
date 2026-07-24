import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import type { Category, Rule } from '@/api'
import { ApiError } from '@/api'
import { Button, Field, Input, Select } from '@/components/primitives'
import { Modal } from '@/components/Modal'
import { useToast } from '@/components/Toast'
import { useCategories } from '@/features/categories/hooks'
import { useCreateRule, useUpdateRule } from './hooks'

const schema = z.object({
  pattern: z.string().trim().min(1).max(200),
  categoryId: z.string().min(1),
  priority: z.string(),
})
type FormValues = z.infer<typeof schema>

export function RuleForm({ open, onClose, rule }: { open: boolean; onClose: () => void; rule?: Rule }) {
  const { t } = useTranslation()
  const toast = useToast()
  const editing = Boolean(rule)
  const create = useCreateRule()
  const update = useUpdateRule()
  const { data: categories } = useCategories()

  const { register, handleSubmit, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: {
      pattern: rule?.pattern ?? '',
      categoryId: rule ? String(rule.categoryId) : '',
      priority: rule ? String(rule.priority) : '0',
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      const categoryId = Number(values.categoryId)
      const priority = Number(values.priority) || 0
      if (rule) {
        await update.mutateAsync({
          id: rule.id,
          body: { version: rule.version, pattern: values.pattern, categoryId, priority },
        })
      } else {
        await create.mutateAsync({ pattern: values.pattern, categoryId, priority })
      }
      toast.success('✓')
      onClose()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))
    }
  })

  const optionLabel = (c: Category) => `${c.name} (${t(`categories.${c.kind}`)})`

  return (
    <Modal
      open={open}
      title={editing ? t('common.edit') : t('rules.new')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={onSubmit} loading={formState.isSubmitting}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Field
          label={t('rules.pattern')}
          htmlFor="rule-pattern"
          hint={t('rules.patternHint')}
          error={formState.errors.pattern && t('errors.required')}
        >
          <Input id="rule-pattern" {...register('pattern')} />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field
            label={t('rules.category')}
            htmlFor="rule-category"
            error={formState.errors.categoryId && t('errors.required')}
          >
            <Select id="rule-category" {...register('categoryId')}>
              <option value="">—</option>
              {(categories ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {optionLabel(c)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('rules.priority')} htmlFor="rule-priority">
            <Input id="rule-priority" type="number" {...register('priority')} />
          </Field>
        </div>
      </form>
    </Modal>
  )
}
