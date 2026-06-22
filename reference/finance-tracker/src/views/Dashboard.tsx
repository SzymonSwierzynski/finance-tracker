import { MonthSummary } from '@/components/MonthSummary'
import { TransactionList } from '@/components/TransactionList'

interface DashboardProps {
  month: string
  currency: string
}

export function Dashboard({ month, currency }: DashboardProps) {
  return (
    <div className="space-y-6">
      <MonthSummary month={month} currency={currency} />
      <div>
        <h2 className="mb-2 text-sm font-semibold text-slate-700">Transactions</h2>
        <TransactionList filter={{ month }} emptyMessage="No transactions this month." />
      </div>
    </div>
  )
}
