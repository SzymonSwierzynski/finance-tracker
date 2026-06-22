import { useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { createTransaction, setRate, useAccounts, useCategories, useSettings } from '@/data'
import type { Category, TransactionType } from '@/data'
import { parseAmountToMinor } from '@/lib/money'
import { todayISO } from '@/lib/date'
import { currencyOptions } from '@/lib/currencies'

type EntryType = Exclude<TransactionType, 'transfer'>

const ENTRY_TYPES: { value: EntryType; label: string }[] = [
  { value: 'expense', label: 'Expense' },
  { value: 'income', label: 'Income' },
]

const inputCls =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-1 focus:ring-slate-500'

function Field({
  label,
  className = '',
  children,
}: {
  label: string
  className?: string
  children: ReactNode
}) {
  return (
    <label className={`flex flex-col gap-1 ${className}`}>
      <span className="text-xs font-medium text-slate-600">{label}</span>
      {children}
    </label>
  )
}

/** A plain decimal exchange rate (comma or dot), must be > 0. */
function parseRate(text: string): number | null {
  const n = Number(text.trim().replace(',', '.'))
  return Number.isFinite(n) && n > 0 ? n : null
}

function categoryLabel(cat: Category, byId: Map<string, Category>): string {
  if (!cat.parentId) return cat.name
  const parent = byId.get(cat.parentId)
  return parent ? `${parent.name} → ${cat.name}` : cat.name
}

export function TransactionForm() {
  const accounts = useAccounts()
  const categories = useCategories()
  const settings = useSettings()
  const reportingCurrency = settings?.reportingCurrency ?? 'PLN'

  const [date, setDate] = useState(todayISO())
  const [type, setType] = useState<EntryType>('expense')
  const [amountText, setAmountText] = useState('')
  const [accountId, setAccountId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [currency, setCurrency] = useState(reportingCurrency)
  const [rateText, setRateText] = useState('')
  const [description, setDescription] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const selectedAccount = useMemo(
    () => accounts?.find((a) => a.id === accountId),
    [accounts, accountId],
  )

  // Default the account (and its currency) once accounts have loaded.
  useEffect(() => {
    if (!accountId && accounts && accounts.length > 0) {
      setAccountId(accounts[0].id)
      setCurrency(accounts[0].currency)
    }
  }, [accounts, accountId])

  const categoriesById = useMemo(() => {
    const m = new Map<string, Category>()
    for (const c of categories ?? []) m.set(c.id, c)
    return m
  }, [categories])

  // Categories of the current kind, parents each followed by their children.
  const categoryChoices = useMemo(() => {
    const list = (categories ?? []).filter((c) => c.kind === type)
    const childrenByParent = new Map<string, Category[]>()
    for (const c of list) {
      if (c.parentId) {
        const arr = childrenByParent.get(c.parentId) ?? []
        arr.push(c)
        childrenByParent.set(c.parentId, arr)
      }
    }
    const ordered: Category[] = []
    for (const p of list.filter((c) => !c.parentId)) {
      ordered.push(p)
      for (const child of childrenByParent.get(p.id) ?? []) ordered.push(child)
    }
    return ordered
  }, [categories, type])

  const isForeign = currency !== reportingCurrency

  // Prefill the rate from saved settings when a foreign currency is selected.
  useEffect(() => {
    if (!isForeign) {
      setRateText('')
      return
    }
    const saved = settings?.rates[currency]
    setRateText(saved != null ? String(saved) : '')
  }, [currency, isForeign, settings])

  // Income and expense categories differ — reset selection when the type flips.
  useEffect(() => {
    setCategoryId('')
  }, [type])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    const amountMinor = parseAmountToMinor(amountText)
    if (amountMinor == null || amountMinor <= 0) {
      setError('Enter a valid amount greater than zero.')
      return
    }
    if (!accountId) {
      setError('Choose an account.')
      return
    }

    let rateToBase = 1
    if (isForeign) {
      const parsed = parseRate(rateText)
      if (parsed == null) {
        setError(`Enter the exchange rate: 1 ${currency} = ? ${reportingCurrency}.`)
        return
      }
      rateToBase = parsed
    }

    setSaving(true)
    try {
      await createTransaction({
        date,
        amountMinor,
        type,
        accountId,
        categoryId: categoryId || null,
        currency,
        rateToBase,
        description,
        note,
      })
      // Remember the rate so it prefills next time.
      if (isForeign) await setRate(currency, rateToBase)
      // Keep date/type/account/currency for quick repeat entry.
      setAmountText('')
      setDescription('')
      setNote('')
      setCategoryId('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save the transaction.')
    } finally {
      setSaving(false)
    }
  }

  const noAccounts = accounts != null && accounts.length === 0
  const currencyChoices = currencyOptions(reportingCurrency, selectedAccount?.currency)

  return (
    <form onSubmit={handleSubmit} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 text-sm font-semibold text-slate-700">Add transaction</h2>

      {noAccounts ? (
        <p className="text-sm text-slate-500">Create an account first (Accounts tab).</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="inline-flex rounded-lg border border-slate-200 p-0.5 sm:col-span-2">
            {ENTRY_TYPES.map((t) => (
              <button
                key={t.value}
                type="button"
                onClick={() => setType(t.value)}
                className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium ${
                  type === t.value ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>

          <Field label="Amount">
            <input
              inputMode="decimal"
              value={amountText}
              onChange={(e) => setAmountText(e.target.value)}
              placeholder="0,00"
              className={inputCls}
            />
          </Field>

          <Field label="Date">
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputCls} />
          </Field>

          <Field label="Account">
            <select
              value={accountId}
              onChange={(e) => {
                setAccountId(e.target.value)
                const acc = accounts?.find((a) => a.id === e.target.value)
                if (acc) setCurrency(acc.currency)
              }}
              className={inputCls}
            >
              {accounts?.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} ({a.currency})
                </option>
              ))}
            </select>
          </Field>

          <Field label="Category">
            <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className={inputCls}>
              <option value="">— None —</option>
              {categoryChoices.map((c) => (
                <option key={c.id} value={c.id}>
                  {categoryLabel(c, categoriesById)}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Currency">
            <select value={currency} onChange={(e) => setCurrency(e.target.value)} className={inputCls}>
              {currencyChoices.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </Field>

          {isForeign && (
            <Field label={`Rate · 1 ${currency} = ? ${reportingCurrency}`}>
              <input
                inputMode="decimal"
                value={rateText}
                onChange={(e) => setRateText(e.target.value)}
                placeholder="0,0000"
                className={inputCls}
              />
            </Field>
          )}

          <Field label="Description" className="sm:col-span-2">
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Biedronka"
              className={inputCls}
            />
          </Field>

          <Field label="Note (optional)" className="sm:col-span-2">
            <input value={note} onChange={(e) => setNote(e.target.value)} className={inputCls} />
          </Field>

          {error && <p className="text-sm text-red-600 sm:col-span-2">{error}</p>}

          <div className="sm:col-span-2">
            <button
              type="submit"
              disabled={saving}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              {saving ? 'Saving…' : 'Add transaction'}
            </button>
          </div>
        </div>
      )}
    </form>
  )
}
