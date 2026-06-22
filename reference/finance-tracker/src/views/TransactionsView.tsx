import { TransactionForm } from '@/components/TransactionForm'
import { TransactionList } from '@/components/TransactionList'

interface TransactionsViewProps {
  month: string
}

export function TransactionsView({ month }: TransactionsViewProps) {
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
      <TransactionForm />
      <TransactionList filter={{ month }} emptyMessage="No transactions this month." />
    </div>
  )
}
