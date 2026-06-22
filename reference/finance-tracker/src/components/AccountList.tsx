import { archiveAccount, updateAccount, useAccounts } from '@/data'
import type { Account } from '@/data'
import { Money } from './Money'

const TYPE_LABEL: Record<Account['type'], string> = {
  checking: 'Checking',
  savings: 'Savings',
  cash: 'Cash',
  credit: 'Credit',
}

function AccountRow({ account }: { account: Account }) {
  return (
    <li className="flex items-center justify-between px-4 py-3">
      <div>
        <div className="font-medium text-slate-800">{account.name}</div>
        <div className="text-xs text-slate-500">
          {TYPE_LABEL[account.type]} · {account.currency}
          {account.trackBalance && account.startingBalanceMinor != null && (
            <>
              {' · start '}
              <Money minor={account.startingBalanceMinor} currency={account.currency} />
            </>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2">
        {account.trackBalance && (
          <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-700">tracked</span>
        )}
        {account.archived ? (
          <button
            type="button"
            onClick={() => updateAccount(account.id, { archived: false })}
            className="text-xs text-slate-500 hover:text-slate-800"
          >
            Unarchive
          </button>
        ) : (
          <button
            type="button"
            onClick={() => archiveAccount(account.id)}
            className="text-xs text-slate-400 hover:text-red-600"
          >
            Archive
          </button>
        )}
      </div>
    </li>
  )
}

export function AccountList() {
  const accounts = useAccounts(true) // include archived

  if (!accounts) return <p className="text-sm text-slate-500">Loading…</p>
  if (accounts.length === 0) return <p className="text-sm text-slate-500">No accounts yet.</p>

  const active = accounts.filter((a) => !a.archived)
  const archived = accounts.filter((a) => a.archived)

  return (
    <div className="space-y-4">
      <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        {active.map((a) => (
          <AccountRow key={a.id} account={a} />
        ))}
        {active.length === 0 && (
          <li className="px-4 py-3 text-sm text-slate-500">No active accounts.</li>
        )}
      </ul>

      {archived.length > 0 && (
        <div>
          <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-400">Archived</h3>
          <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white opacity-70">
            {archived.map((a) => (
              <AccountRow key={a.id} account={a} />
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
