import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { ApiError, RECURRING_FREQUENCIES } from '@/api'
import { Button, Field, Input, Select } from '@/components/primitives'
import { Modal } from '@/components/Modal'
import { useToast } from '@/components/Toast'
import { useAccounts } from '@/features/accounts/hooks'
import { useCategories } from '@/features/categories/hooks'
import { todayIso } from '@/lib/date'
import { useCreateRecurring } from './hooks'

const schema = z.object({
  accountId: z.string().min(1),
  type: z.enum(['expense', 'income']),
  amount: z.string().min(1),
  categoryId: z.string(),
  frequency: z.enum(['daily', 'weekly', 'monthly', 'yearly']),
  intervalCount: z.string(),
  startDate: z.string().min(1),
  endDate: z.string(),
  description: z.string(),
})
type FormValues = z.infer<typeof schema>

export function RecurringForm({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const create = useCreateRecurring()
  const { data: accounts } = useAccounts(false)

  const { register, handleSubmit, watch, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: {
      accountId: '',
      type: 'expense',
      amount: '',
      categoryId: '',
      frequency: 'monthly',
      intervalCount: '1',
      startDate: todayIso(),
      endDate: '',
      description: '',
    },
  })

  const type = watch('type')
  const { data: categories } = useCategories(type) // already filtered to the type's kind

  const onSubmit = handleSubmit(async (values) => {
    try {
      await create.mutateAsync({
        accountId: Number(values.accountId),
        amountMinor: Math.round(Number(values.amount) * 100),
        type: values.type,
        categoryId: values.categoryId ? Number(values.categoryId) : undefined,
        frequency: values.frequency,
        intervalCount: values.intervalCount ? Number(values.intervalCount) : 1,
        startDate: values.startDate,
        endDate: values.endDate || undefined,
        description: values.description,
      })
      toast.success('✓')
      onClose()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))
    }
  })

  return (
    <Modal
      open={open}
      title={t('recurring.new')}
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
        <div className="grid grid-cols-2 gap-4">
          <Field
            label={t('transactions.account')}
            htmlFor="rec-account"
            error={formState.errors.accountId && t('errors.required')}
          >
            <Select id="rec-account" {...register('accountId')}>
              <option value="">—</option>
              {(accounts ?? []).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('transactions.type')} htmlFor="rec-type">
            <Select id="rec-type" {...register('type')}>
              <option value="expense">{t('transactions.expense')}</option>
              <option value="income">{t('transactions.income')}</option>
            </Select>
          </Field>
          <Field
            label={t('transactions.amount')}
            htmlFor="rec-amount"
            error={formState.errors.amount && t('errors.required')}
          >
            <Input id="rec-amount" inputMode="decimal" placeholder="0.00" {...register('amount')} />
          </Field>
          <Field
            label={`${t('transactions.category')} (${t('common.optional')})`}
            htmlFor="rec-cat"
          >
            <Select id="rec-cat" {...register('categoryId')}>
              <option value="">{t('common.none')}</option>
              {(categories ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.parentId == null ? c.name : `· ${c.name}`}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('recurring.frequency')} htmlFor="rec-freq">
            <Select id="rec-freq" {...register('frequency')}>
              {RECURRING_FREQUENCIES.map((f) => (
                <option key={f} value={f}>
                  {t(`recurring.${f}`)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('recurring.every')} htmlFor="rec-interval">
            <Input id="rec-interval" type="number" min={1} {...register('intervalCount')} />
          </Field>
          <Field label={t('recurring.startDate')} htmlFor="rec-start">
            <Input id="rec-start" type="date" {...register('startDate')} />
          </Field>
          <Field
            label={`${t('recurring.endDate')} (${t('common.optional')})`}
            htmlFor="rec-end"
          >
            <Input id="rec-end" type="date" {...register('endDate')} />
          </Field>
        </div>
        <Field label={t('transactions.description')} htmlFor="rec-desc">
          <Input id="rec-desc" {...register('description')} />
        </Field>
      </form>
    </Modal>
  )
}
