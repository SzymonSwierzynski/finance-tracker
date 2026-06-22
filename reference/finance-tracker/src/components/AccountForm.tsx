import { useState } from 'react'
import type { FormEvent } from 'react'
import { createAccount } from '@/data'
import type { AccountType } from '@/data'
import { parseAmountToMinor } from '@/lib/money'
import { currencyOptions } from '@/lib/currencies'

const ACCOUNT_TYPES: { value: AccountType; label: string }[] = [
  { value: 'checking', label: 'Checking' },
  { value: 'savings', label: 'Savings' },
  { value: 'cash', label: 'Cash' },
  { value: 'credit', label: 'Credit' },
]

const inputCls =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-1 focus:ring-slate-500'

export function AccountForm() {
  const [name, setName] = useState('')
  const [type, setType] = useState<AccountType>('checking')
  const [currency, setCurrency] = useState('PLN')
  const [trackBalance, setTrackBalance] = useState(false)
  const [startingText, setStartingText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent): Promise<void> {
    e.preventDefault()
    setError(null)

    if (!name.trim()) {
      setError('Give the account a name.')
      return
    }

    let startingBalanceMinor: number | null = null
    if (trackBalance && startingText.trim()) {
      const parsed = parseAmountToMinor(startingText)
      if (parsed == null) {
        setError('Starting balance is not a valid amount.')
        return
      }
      startingBalanceMinor = parsed
    }

    setSaving(true)
    try {
      await createAccount({ name, type, currency, trackBalance, startingBalanceMinor })
      setName('')
      setStartingText('')
      setTrackBalance(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the account.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 text-sm font-semibold text-slate-700">Add account</h2>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 sm:col-span-2">
          <span className="text-xs font-medium text-slate-600">Name</span>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. mBank checking"
            className={inputCls}
          />
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-slate-600">Type</span>
          <select value={type} onChange={(e) => setType(e.target.value as AccountType)} className={inputCls}>
            {ACCOUNT_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-slate-600">Currency</span>
          <select value={currency} onChange={(e) => setCurrency(e.target.value)} className={inputCls}>
            {currencyOptions('PLN').map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-2 sm:col-span-2">
          <input
            type="checkbox"
            checked={trackBalance}
            onChange={(e) => setTrackBalance(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300"
          />
          <span className="text-sm text-slate-700">Track balance for this account</span>
        </label>

        {trackBalance && (
          <label className="flex flex-col gap-1 sm:col-span-2">
            <span className="text-xs font-medium text-slate-600">Starting balance ({currency})</span>
            <input
              inputMode="decimal"
              value={startingText}
              onChange={(e) => setStartingText(e.target.value)}
              placeholder="0,00"
              className={inputCls}
            />
          </label>
        )}

        {error && <p className="text-sm text-red-600 sm:col-span-2">{error}</p>}

        <div className="sm:col-span-2">
          <button
            type="submit"
            disabled={saving}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {saving ? 'Saving…' : 'Add account'}
          </button>
        </div>
      </div>
    </form>
  )
}
