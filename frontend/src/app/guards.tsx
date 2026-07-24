import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '@/features/auth/AuthProvider'
import { Spinner } from '@/components/primitives'
import { AppLayout } from './AppLayout'

function FullScreenLoader() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-surface-2 text-accent">
      <Spinner className="size-8" />
    </div>
  )
}

/** Gate for protected routes: waits for session bootstrap, then layout or redirect to login. */
export function RequireAuth() {
  const { status } = useAuth()
  if (status === 'loading') return <FullScreenLoader />
  if (status === 'anon') return <Navigate to="/login" replace />
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}

/** Keeps authenticated users away from the login/register screens. */
export function AnonOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <FullScreenLoader />
  if (status === 'authed') return <Navigate to="/" replace />
  return <>{children}</>
}
