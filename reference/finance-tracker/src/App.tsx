import { lazy, Suspense, useEffect, useState } from 'react'
import { ensureSeeded, useSettings } from '@/data'
import { currentMonth } from '@/lib/date'
import { MonthSwitcher } from '@/components/MonthSwitcher'
import { Dashboard } from '@/views/Dashboard'
import { TransactionsView } from '@/views/TransactionsView'
import { AccountsView } from '@/views/AccountsView'
import { RulesView } from '@/views/RulesView'

// Recharts / papaparse are heavy and view-specific, so load these on demand —
// each becomes its own chunk, keeping the initial load light.
const BreakdownView = lazy(() =>
  import('@/views/BreakdownView').then((m) => ({ default: m.BreakdownView })),
)
const ImportView = lazy(() => import('@/views/ImportView').then((m) => ({ default: m.ImportView })))

type Tab = 'dashboard' | 'breakdown' | 'transactions' | 'import' | 'rules' | 'accounts'

const TABS: { id: Tab; label: string }[] = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'breakdown', label: 'Breakdown' },
  { id: 'transactions', label: 'Transactions' },
  { id: 'import', label: 'Import' },
  { id: 'rules', label: 'Rules' },
  { id: 'accounts', label: 'Accounts' },
]

export default function App() {
  const [ready, setReady] = useState(false)
  const [tab, setTab] = useState<Tab>('dashboard')
  const [month, setMonth] = useState(currentMonth())
  const settings = useSettings()
  const reportingCurrency = settings?.reportingCurrency ?? 'PLN'

  useEffect(() => {
    let active = true
    ensureSeeded()
      .catch((e) => console.error('Seeding failed', e))
      .finally(() => {
        if (active) setReady(true)
      })
    return () => {
      active = false
    }
  }, [])

  if (!ready) {
    return <div className="grid min-h-screen place-items-center text-slate-400">Loading…</div>
  }

  // Breakdown has its own period selector; Accounts is period-agnostic.
  const showMonth = tab === 'dashboard' || tab === 'transactions'

  return (
    <div className="min-h-screen text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-4">
          <div>
            <h1 className="text-lg font-semibold">Finance Tracker</h1>
            <p className="text-xs text-slate-500">Reporting in {reportingCurrency}</p>
          </div>
          {showMonth && <MonthSwitcher month={month} onChange={setMonth} />}
        </div>
        <nav className="mx-auto flex max-w-5xl gap-1 px-4">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
                tab === t.id
                  ? 'border-slate-900 text-slate-900'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6">
        {tab === 'dashboard' && <Dashboard month={month} currency={reportingCurrency} />}
        {tab === 'breakdown' && (
          <Suspense fallback={<p className="text-sm text-slate-400">Loading…</p>}>
            <BreakdownView />
          </Suspense>
        )}
        {tab === 'transactions' && <TransactionsView month={month} />}
        {tab === 'import' && (
          <Suspense fallback={<p className="text-sm text-slate-400">Loading…</p>}>
            <ImportView />
          </Suspense>
        )}
        {tab === 'rules' && <RulesView />}
        {tab === 'accounts' && <AccountsView />}
      </main>
    </div>
  )
}
