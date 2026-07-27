import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { ApiError, type BudgetProgress } from '@/api'
import { Button, Field, Input, Select } from '@/components/primitives'
import { Modal } from '@/components/Modal'
import { useToast } from '@/components/Toast'
import { useCategories } from '@/features/categories/hooks'
import { useCreateBudget, useUpdateBudget } from './hooks'

const schema = z.object({ categoryId: z.string(), amount: z.string().min(1), rollover: z.boolean() })
type FormValues = z.infer<typeof schema>

export function BudgetForm({
  open,
  onClose,
  edit,
  budgetedCategoryIds,
}: {
  open: boolean
  onClose: () => void
  edit?: BudgetProgress
  budgetedCategoryIds: number[]
}) {
  const { t } = useTranslation()
  const toast = useToast()
  const create = useCreateBudget()
  const update = useUpdateBudget()
  const { data: categories } = useCategories('expense')

  // On create, only offer expense categories that don't already have a budget.
  const options = (categories ?? []).filter((c) => !budgetedCategoryIds.includes(c.id))

  const { register, handleSubmit, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: {
      categoryId: edit ? String(edit.categoryId) : '',
      amount: edit ? (edit.amountMinor / 100).toFixed(2) : '',
      rollover: edit ? edit.rollover : false,
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    const amountMinor = Math.round(Number(values.amount) * 100)
    if (!Number.isFinite(amountMinor) || amountMinor <= 0) {
      toast.error(t('budgets.invalidAmount'))
      return
    }
    try {
      if (edit) {
        await update.mutateAsync({
          id: edit.id,
          body: { amountMinor, version: edit.version, rollover: values.rollover },
        })
      } else {
        if (!values.categoryId) {
          toast.error(t('errors.required'))
          return
        }
        await create.mutateAsync({
          categoryId: Number(values.categoryId),
          amountMinor,
          rollover: values.rollover,
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
      title={edit ? t('budgets.edit') : t('budgets.new')}
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
        {edit ? (
          <Field label={t('budgets.category')} htmlFor="bud-cat">
            <Input id="bud-cat" value={edit.categoryName} disabled />
          </Field>
        ) : (
          <Field
            label={t('budgets.category')}
            htmlFor="bud-cat"
            error={formState.errors.categoryId && t('errors.required')}
          >
            <Select id="bud-cat" {...register('categoryId')}>
              <option value="">—</option>
              {options.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.parentId == null ? c.name : `· ${c.name}`}
                </option>
              ))}
            </Select>
          </Field>
        )}
        <Field
          label={t('budgets.monthlyLimit')}
          htmlFor="bud-amount"
          error={formState.errors.amount && t('errors.required')}
        >
          <Input id="bud-amount" inputMode="decimal" placeholder="0.00" {...register('amount')} />
        </Field>
        <label className="flex items-start gap-2 text-sm text-fg">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-border"
            {...register('rollover')}
          />
          <span>
            {t('budgets.rollover')}
            <span className="mt-0.5 block text-xs text-fg-soft">{t('budgets.rolloverHelp')}</span>
          </span>
        </label>
      </form>
    </Modal>
  )
}
