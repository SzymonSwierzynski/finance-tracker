import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/AuthProvider'

const navItems = [
  { to: '/', key: 'nav.dashboard', end: true },
  { to: '/breakdown', key: 'nav.breakdown', end: false },
  { to: '/trends', key: 'nav.trends', end: false },
  { to: '/transactions', key: 'nav.transactions', end: false },
  { to: '/import', key: 'nav.import', end: false },
  { to: '/rules', key: 'nav.rules', end: false },
  { to: '/accounts', key: 'nav.accounts', end: false },
  { to: '/categories', key: 'nav.categories', end: false },
  { to: '/settings', key: 'nav.settings', end: false },
] as const

export function AppLayout({ children }: { children: ReactNode }) {
  const { t, i18n } = useTranslation()
  const { user, logout } = useAuth()

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`

  const toggleLang = () => void i18n.changeLanguage(i18n.language.startsWith('pl') ? 'en' : 'pl')

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_1fr]">
      {/* Sidebar (desktop) */}
      <aside className="hidden border-r border-slate-200 bg-white lg:flex lg:flex-col">
        <div className="flex items-center gap-2 px-6 py-5">
          <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600 font-bold text-white">₣</span>
          <span className="font-semibold text-slate-900">{t('app.name')}</span>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
              {t(item.key)}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-slate-200 p-3">
          <p className="truncate px-3 pb-2 text-xs text-slate-500">{user?.email}</p>
          <button onClick={() => void logout()} className="w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-600 hover:bg-slate-100">
            {t('nav.logout')}
          </button>
        </div>
      </aside>

      {/* Main column */}
      <div className="flex min-h-dvh flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 lg:px-8">
          {/* Mobile nav */}
          <nav className="flex gap-1 overflow-x-auto lg:hidden">
            {navItems.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                {t(item.key)}
              </NavLink>
            ))}
          </nav>
          <div className="hidden lg:block" />
          <div className="flex items-center gap-2">
            <button
              onClick={toggleLang}
              className="rounded-lg px-2.5 py-1.5 text-xs font-semibold uppercase text-slate-500 ring-1 ring-inset ring-slate-200 hover:bg-slate-50"
              aria-label="Toggle language"
            >
              {i18n.language.startsWith('pl') ? 'PL' : 'EN'}
            </button>
            <button onClick={() => void logout()} className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-100 lg:hidden">
              {t('nav.logout')}
            </button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 lg:px-8 lg:py-8">{children}</main>
      </div>
    </div>
  )
}
