import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { AccountsPage } from '@/features/accounts/AccountsPage'
import { TransactionsPage } from '@/features/transactions/TransactionsPage'
import { CategoriesPage } from '@/features/categories/CategoriesPage'
import { BreakdownPage } from '@/features/breakdown/BreakdownPage'
import { TrendsPage } from '@/features/trends/TrendsPage'
import { ImportPage } from '@/features/import/ImportPage'
import { RulesPage } from '@/features/rules/RulesPage'
import { RecurringPage } from '@/features/recurring/RecurringPage'
import { SettingsPage } from '@/features/settings/SettingsPage'
import { AnonOnly, RequireAuth } from './guards'

export function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <AnonOnly>
            <LoginPage />
          </AnonOnly>
        }
      />
      <Route
        path="/register"
        element={
          <AnonOnly>
            <RegisterPage />
          </AnonOnly>
        }
      />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/breakdown" element={<BreakdownPage />} />
        <Route path="/trends" element={<TrendsPage />} />
        <Route path="/categories" element={<CategoriesPage />} />
        <Route path="/import" element={<ImportPage />} />
        <Route path="/rules" element={<RulesPage />} />
        <Route path="/recurring" element={<RecurringPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
