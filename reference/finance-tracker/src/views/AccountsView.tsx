import { AccountForm } from '@/components/AccountForm'
import { AccountList } from '@/components/AccountList'

export function AccountsView() {
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <AccountForm />
      <AccountList />
    </div>
  )
}
