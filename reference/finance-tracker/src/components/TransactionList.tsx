import {
  deleteTransaction,
  useAccounts,
  useCategories,
  useSettings,
  useTransactions,
} from '@/data'
import type { Transaction, TransactionFilter } from '@/data'
import { toBaseMinor } from '@/lib/money'
import { Money } from './Money'

interface TransactionListProps {
  filter: TransactionFilter
  emptyMessage?: string
}

export function TransactionList({ filter, emptyMessage = 'No transactions.' }: TransactionListProps) {
  const transactions = useTransactions(filter)
  const accounts = useAccounts(true)
  const categories = useCategories()
  const settings = useSettings()
  const reportingCurrency = settings?.reportingCurrency ?? 'PLN'

  function accountName(id: string): string {
    return accounts?.find((a) => a.id === id)?.name ?? '—'
  }

  function categoryName(id: string | null): string {
    if (!id) return '—'
    const c = categories?.find((x) => x.id === id)
    if (!c) return '—'
    const parent = c.parentId ? categories?.find((x) => x.id === c.parentId) : undefined
    return parent ? `${parent.name} → ${c.name}` : c.name
  }

  async function handleDelete(t: Transaction): Promise<void> {
    if (window.confirm(`Delete "${t.description || 'transaction'}"?`)) {
      await deleteTransaction(t.id)
    }
  }

  if (!transactions) {
    return <p className="text-sm text-slate-500">Loading…</p>
  }
  if (transactions.length === 0) {
    return (
      <p className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center text-sm text-slate-500">
        {emptyMessage}
      </p>
    )
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-3 py-2 font-medium">Date</th>
            <th className="px-3 py-2 font-medium">Description</th>
            <th className="px-3 py-2 font-medium">Category</th>
            <th className="px-3 py-2 font-medium">Account</th>
            <th className="px-3 py-2 text-right font-medium">Amount</th>
            <th className="px-3 py-2" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {transactions.map((t) => {
            const sign = t.type === 'expense' ? '−' : t.type === 'income' ? '+' : ''
            const amtColor =
              t.type === 'expense'
                ? 'text-red-600'
                : t.type === 'income'
                  ? 'text-emerald-600'
                  : 'text-slate-600'
            const showBase = t.currency !== reportingCurrency
            return (
              <tr key={t.id} className="hover:bg-slate-50">
                <td className="whitespace-nowrap px-3 py-2 text-slate-600">{t.date}</td>
                <td className="px-3 py-2">
                  <div className="font-medium text-slate-800">
                    {t.description || <span className="text-slate-400">(no description)</span>}
                  </div>
                  {t.note && <div className="text-xs text-slate-400">{t.note}</div>}
                </td>
                <td className="px-3 py-2 text-slate-600">{categoryName(t.categoryId)}</td>
                <td className="px-3 py-2 text-slate-600">{accountName(t.accountId)}</td>
                <td className="px-3 py-2 text-right">
                  <div className={`font-medium ${amtColor}`}>
                    {sign}
                    <Money minor={t.amountMinor} currency={t.currency} />
                  </div>
                  {showBase && (
                    <div className="text-xs text-slate-400">
                      ≈ <Money minor={toBaseMinor(t.amountMinor, t.rateToBase)} currency={reportingCurrency} />
                    </div>
                  )}
                </td>
                <td className="px-3 py-2 text-right">
                  <button
                    type="button"
                    onClick={() => handleDelete(t)}
                    className="rounded-md px-2 py-1 text-xs text-slate-400 hover:bg-red-50 hover:text-red-600"
                  >
                    Delete
                  </button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
