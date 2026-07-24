import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import type { Account, AccountType } from '@/api'
import { ApiError, ACCOUNT_TYPES } from '@/api'
import { Button, Field, Input, Select } from '@/components/primitives'
import { Modal } from '@/components/Modal'
import { useToast } from '@/components/Toast'
import { parseAmountToMinor } from '@/lib/money'
import { useCreateAccount, useUpdateAccount } from './hooks'

const schema = z.object({
  name: z.string().trim().min(1).max(100),
  type: z.enum(['checking', 'savings', 'cash', 'credit']),
  currency: z.string().regex(/^[A-Za-z]{3}$/, 'ISO 4217'),
  trackBalance: z.boolean(),
  startingBalance: z.string(),
})
type FormValues = z.infer<typeof schema>

function minorToInput(minor: number | null): string {
  return minor == null ? '' : (minor / 100).toFixed(2)
}

export function AccountForm({ open, onClose, account }: { open: boolean; onClose: () => void; account?: Account }) {
  const { t } = useTranslation()
  const toast = useToast()
  const create = useCreateAccount()
  const update = useUpdateAccount()
  const editing = Boolean(account)

  const { register, handleSubmit, watch, setError, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    values: {
      name: account?.name ?? '',
      type: account?.type ?? 'checking',
      currency: account?.currency ?? 'PLN',
      trackBalance: account?.trackBalance ?? false,
      startingBalance: minorToInput(account?.startingBalanceMinor ?? null),
    },
  })

  const trackBalance = watch('trackBalance')

  const onSubmit = handleSubmit(async (values) => {
    // Omitted (undefined) when not tracking; the backend treats absent as null and also nulls it
    // whenever trackBalance is false, so we never need to send an explicit null.
    let startingBalanceMinor: number | undefined
    if (values.trackBalance && values.startingBalance.trim() !== '') {
      const parsed = parseAmountToMinor(values.startingBalance)
      if (parsed == null) {
        setError('startingBalance', { message: 'invalid' })
        return
      }
      startingBalanceMinor = parsed
    }

    try {
      if (account) {
        await update.mutateAsync({
          id: account.id,
          body: {
            version: account.version,
            name: values.name,
            type: values.type as AccountType,
            trackBalance: values.trackBalance,
            startingBalanceMinor,
          },
        })
      } else {
        await create.mutateAsync({
          name: values.name,
          type: values.type as AccountType,
          currency: values.currency.toUpperCase(),
          trackBalance: values.trackBalance,
          startingBalanceMinor,
        })
      }
      toast.success(editing ? `${values.name} ✓` : `${values.name} ✓`)
      onClose()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))
    }
  })

  return (
    <Modal
      open={open}
      title={editing ? t('common.edit') : t('accounts.new')}
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
        <Field label={t('accounts.name')} htmlFor="acc-name" error={formState.errors.name && t('errors.required')}>
          <Input id="acc-name" {...register('name')} />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label={t('accounts.type')} htmlFor="acc-type">
            <Select id="acc-type" {...register('type')}>
              {ACCOUNT_TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`accounts.${type}`)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('accounts.currency')} htmlFor="acc-ccy" error={formState.errors.currency?.message}>
            <Input id="acc-ccy" maxLength={3} className="uppercase" disabled={editing} {...register('currency')} />
          </Field>
        </div>
        <label className="flex items-center gap-2 text-sm font-medium text-fg-muted">
          <input type="checkbox" className="size-4 rounded border-border-strong text-accent" {...register('trackBalance')} />
          {t('accounts.trackBalance')}
        </label>
        {trackBalance && (
          <Field label={t('accounts.startingBalance')} htmlFor="acc-start" error={formState.errors.startingBalance && 'invalid'}>
            <Input id="acc-start" inputMode="decimal" placeholder="0.00" {...register('startingBalance')} />
          </Field>
        )}
      </form>
    </Modal>
  )
}
