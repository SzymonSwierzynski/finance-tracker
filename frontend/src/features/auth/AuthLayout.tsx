import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/** Branded, centered panel shared by the login and register screens. */
export function AuthLayout({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  const { t } = useTranslation()
  return (
    <div className="flex min-h-dvh items-center justify-center bg-gradient-to-br from-accent-soft via-surface-2 to-surface-2 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 flex size-12 items-center justify-center rounded-xl bg-brand-600 text-xl font-bold text-white">
            ₣
          </div>
          <h1 className="text-xl font-semibold text-fg">{t('app.name')}</h1>
          <p className="text-sm text-fg-soft">{t('app.tagline')}</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-fg">{title}</h2>
          {children}
        </div>
        <p className="mt-4 text-center text-sm text-fg-soft">{footer}</p>
      </div>
    </div>
  )
}
