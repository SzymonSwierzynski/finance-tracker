import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import type { Category, CategoryKind } from '@/api'
import { ApiError, CATEGORY_KINDS } from '@/api'
import { Button, Field, Input, Select } from '@/components/primitives'
import { Modal } from '@/components/Modal'
import { useToast } from '@/components/Toast'
import { useCategories, useCreateCategory, useUpdateCategory } from './hooks'

const schema = z.object({
  name: z.string().trim().min(1).max(100),
  kind: z.enum(['expense', 'income']),
  parentId: z.string(),
  color: z.string().regex(/^#[0-9a-fA-F]{6}$/),
})
type FormValues = z.infer<typeof schema>

export function CategoryForm({
  open,
  onClose,
  category,
  defaultKind,
}: {
  open: boolean
  onClose: () => void
  category?: Category
  defaultKind: CategoryKind
}) {
  const { t } = useTranslation()
  const toast = useToast()
  const editing = Boolean(category)
  const create = useCreateCategory()
  const update = useUpdateCategory()

  const { register, handleSubmit, watch, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: {
      name: category?.name ?? '',
      kind: category?.kind ?? defaultKind,
      parentId: category?.parentId ? String(category.parentId) : '',
      color: category?.color ?? '#6366f1',
    },
  })

  const kind = watch('kind')
  const { data: kindCategories } = useCategories(kind)
  const topLevel = (kindCategories ?? []).filter((c) => c.parentId == null && c.id !== category?.id)

  const onSubmit = handleSubmit(async (values) => {
    try {
      if (category) {
        await update.mutateAsync({
          id: category.id,
          body: { version: category.version, name: values.name, color: values.color },
        })
      } else {
        await create.mutateAsync({
          name: values.name,
          kind: values.kind as CategoryKind,
          parentId: values.parentId ? Number(values.parentId) : undefined,
          color: values.color,
        })
      }
      toast.success('✓')
      onClose()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))
    }
  })

  return (
    <Modal
      open={open}
      title={editing ? t('common.edit') : t('categories.new')}
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
        <Field label={t('categories.name')} htmlFor="cat-name" error={formState.errors.name && t('errors.required')}>
          <Input id="cat-name" {...register('name')} />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label={t('categories.kind')} htmlFor="cat-kind">
            <Select id="cat-kind" disabled={editing} {...register('kind')}>
              {CATEGORY_KINDS.map((k) => (
                <option key={k} value={k}>
                  {t(`categories.${k}`)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('categories.color')} htmlFor="cat-color">
            <input
              id="cat-color"
              type="color"
              className="h-10 w-full cursor-pointer rounded-lg border border-border-strong bg-surface p-1"
              {...register('color')}
            />
          </Field>
        </div>
        {!editing && (
          <Field label={`${t('categories.parent')} (${t('common.optional')})`} htmlFor="cat-parent">
            <Select id="cat-parent" {...register('parentId')}>
              <option value="">{t('categories.topLevel')}</option>
              {topLevel.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </Field>
        )}
      </form>
    </Modal>
  )
}
