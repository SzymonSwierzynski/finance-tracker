import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '@/features/auth/LoginPage'
import { RegisterPage } from '@/features/auth/RegisterPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { AccountsPage } from '@/features/accounts/AccountsPage'
import { TransactionsPage } from '@/features/transactions/TransactionsPage'
import { CategoriesPage } from '@/features/categories/CategoriesPage'
import { BreakdownPage } from '@/features/breakdown/BreakdownPage'
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
        <Route path="/categories" element={<CategoriesPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
