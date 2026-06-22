import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import type { Transaction, TransactionType } from '@/api'
import { ApiError, TRANSACTION_TYPES } from '@/api'
import { Button, Field, Input, Select, TextArea } from '@/components/primitives'
import { Modal } from '@/components/Modal'
import { useToast } from '@/components/Toast'
import { parseAmountToMinor } from '@/lib/money'
import { todayIso } from '@/lib/date'
import { useAccounts } from '@/features/accounts/hooks'
import { useCategories } from '@/features/categories/hooks'
import { useCreateTransaction, useUpdateTransaction } from './hooks'

const schema = z
  .object({
    date: z.string().min(1),
    type: z.enum(['expense', 'income', 'transfer']),
    accountId: z.string().min(1, 'required'),
    counterAccountId: z.string(),
    categoryId: z.string(),
    amount: z.string().min(1, 'required'),
    currency: z.string(),
    rateToBase: z.string(),
    description: z.string().max(500),
    note: z.string().max(1000),
  })
  .superRefine((v, ctx) => {
    if (parseAmountToMinor(v.amount) == null || (parseAmountToMinor(v.amount) ?? 0) <= 0) {
      ctx.addIssue({ path: ['amount'], code: 'custom', message: 'invalid' })
    }
    if (v.currency !== '' && !/^[A-Za-z]{3}$/.test(v.currency)) {
      ctx.addIssue({ path: ['currency'], code: 'custom', message: 'ISO 4217' })
    }
    if (v.type === 'transfer') {
      if (!v.counterAccountId) ctx.addIssue({ path: ['counterAccountId'], code: 'custom', message: 'required' })
      else if (v.counterAccountId === v.accountId)
        ctx.addIssue({ path: ['counterAccountId'], code: 'custom', message: 'distinct' })
    }
  })
type FormValues = z.infer<typeof schema>

function minorToInput(minor: number): string {
  return (minor / 100).toFixed(2)
}

export function TransactionForm({
  open,
  onClose,
  transaction,
}: {
  open: boolean
  onClose: () => void
  transaction?: Transaction
}) {
  const { t } = useTranslation()
  const toast = useToast()
  const editing = Boolean(transaction)
  const { data: accounts } = useAccounts(false)
  const { data: categories } = useCategories()
  const create = useCreateTransaction()
  const update = useUpdateTransaction()

  const { register, handleSubmit, watch, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    resetOptions: { keepDirtyValues: true },
    values: {
      date: transaction?.date ?? todayIso(),
      type: transaction?.type ?? 'expense',
      accountId: transaction ? String(transaction.accountId) : '',
      counterAccountId: transaction?.counterAccountId ? String(transaction.counterAccountId) : '',
      categoryId: transaction?.categoryId ? String(transaction.categoryId) : '',
      amount: transaction ? minorToInput(transaction.amountMinor) : '',
      currency: transaction?.currency ?? '',
      rateToBase: transaction ? String(transaction.rateToBase) : '',
      description: transaction?.description ?? '',
      note: transaction?.note ?? '',
    },
  })

  const type = watch('type')
  const selectedAccountId = watch('accountId')
  const options = accounts ?? []
  // Category applies to expense/income only; options match the transaction's kind.
  const effectiveType = transaction?.type ?? type
  const categoryOptions = (categories ?? []).filter(
    (c) => c.kind === (effectiveType === 'income' ? 'income' : 'expense'),
  )
  const categoryLabel = (id: number): string => {
    const c = categoryOptions.find((x) => x.id === id)
    if (!c) return ''
    if (c.parentId == null) return c.name
    const parent = categoryOptions.find((x) => x.id === c.parentId)
    return parent ? `${parent.name} / ${c.name}` : c.name
  }

  const onSubmit = handleSubmit(async (values) => {
    const amountMinor = parseAmountToMinor(values.amount)
    if (amountMinor == null || amountMinor <= 0) return

    try {
      const categoryId = values.categoryId ? Number(values.categoryId) : undefined
      if (transaction) {
        await update.mutateAsync({
          id: transaction.id,
          body: {
            version: transaction.version,
            date: values.date,
            amountMinor,
            // The server applies categoryId as given; omitting it (undefined) uncategorizes.
            // Transfers can't be categorized, so always omit for them.
            categoryId: transaction.type === 'transfer' ? undefined : categoryId,
            description: values.description,
            note: values.note,
          },
        })
      } else {
        await create.mutateAsync({
          date: values.date,
          amountMinor,
          type: values.type as TransactionType,
          accountId: Number(values.accountId),
          counterAccountId: values.type === 'transfer' ? Number(values.counterAccountId) : undefined,
          categoryId: values.type === 'transfer' ? undefined : categoryId,
          currency: values.currency ? values.currency.toUpperCase() : undefined,
          rateToBase: values.rateToBase ? Number(values.rateToBase) : undefined,
          description: values.description || undefined,
          note: values.note || undefined,
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
      title={editing ? t('common.edit') : t('transactions.new')}
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
          <Field label={t('transactions.date')} htmlFor="tx-date" error={formState.errors.date && t('errors.required')}>
            <Input id="tx-date" type="date" {...register('date')} />
          </Field>
          <Field label={t('transactions.amount')} htmlFor="tx-amount" error={formState.errors.amount && 'invalid'}>
            <Input id="tx-amount" inputMode="decimal" placeholder="0.00" {...register('amount')} />
          </Field>
        </div>

        {!editing && (
          <>
            <Field label={t('transactions.type')} htmlFor="tx-type">
              <Select id="tx-type" {...register('type')}>
                {TRANSACTION_TYPES.map((tt) => (
                  <option key={tt} value={tt}>
                    {t(`transactions.${tt}`)}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label={t('transactions.account')} htmlFor="tx-account" error={formState.errors.accountId && t('errors.required')}>
              <Select id="tx-account" {...register('accountId')}>
                <option value="">—</option>
                {options.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} ({a.currency})
                  </option>
                ))}
              </Select>
            </Field>
            {type === 'transfer' && (
              <Field
                label={t('transactions.counterAccount')}
                htmlFor="tx-counter"
                error={formState.errors.counterAccountId && t('errors.required')}
              >
                <Select id="tx-counter" {...register('counterAccountId')}>
                  <option value="">—</option>
                  {options
                    .filter((a) => String(a.id) !== selectedAccountId)
                    .map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name} ({a.currency})
                      </option>
                    ))}
                </Select>
              </Field>
            )}
            <div className="grid grid-cols-2 gap-4">
              <Field label={`${t('accounts.currency')} (${t('common.optional')})`} htmlFor="tx-ccy" error={formState.errors.currency?.message}>
                <Input id="tx-ccy" maxLength={3} className="uppercase" placeholder="PLN" {...register('currency')} />
              </Field>
              <Field label={t('transactions.rate')} htmlFor="tx-rate" hint={t('transactions.rateHint')}>
                <Input id="tx-rate" inputMode="decimal" placeholder="1.0" {...register('rateToBase')} />
              </Field>
            </div>
          </>
        )}

        {effectiveType !== 'transfer' && (
          <Field label={`${t('transactions.category')} (${t('common.optional')})`} htmlFor="tx-cat">
            <Select id="tx-cat" {...register('categoryId')}>
              <option value="">{t('breakdown.uncategorized')}</option>
              {categoryOptions.map((c) => (
                <option key={c.id} value={c.id}>
                  {categoryLabel(c.id)}
                </option>
              ))}
            </Select>
          </Field>
        )}

        <Field label={`${t('transactions.description')} (${t('common.optional')})`} htmlFor="tx-desc">
          <Input id="tx-desc" {...register('description')} />
        </Field>
        <Field label={`${t('transactions.note')} (${t('common.optional')})`} htmlFor="tx-note">
          <TextArea id="tx-note" rows={2} {...register('note')} />
        </Field>
      </form>
    </Modal>
  )
}
