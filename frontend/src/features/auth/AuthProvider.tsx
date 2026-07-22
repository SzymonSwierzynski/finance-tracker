import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { refreshSession, setAccessToken } from '@/api'
import type { UserProfile } from '@/api'
import { authApi } from './api'

type Status = 'loading' | 'authed' | 'anon'

interface AuthContextValue {
  user: UserProfile | null
  status: Status
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName?: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [status, setStatus] = useState<Status>('loading')
  const queryClient = useQueryClient()

  // Restore the session on load: a valid HttpOnly refresh cookie mints a fresh access token.
  useEffect(() => {
    let active = true
    void refreshSession().then((token) => {
      if (!active) return
      if (token) {
        setUser(token.user)
        setStatus('authed')
      } else {
        setStatus('anon')
      }
    })
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      const token = await authApi.login({ email, password })
      setAccessToken(token.accessToken)
      queryClient.clear()
      setUser(token.user)
      setStatus('authed')
    },
    [queryClient],
  )

  const register = useCallback(
    async (email: string, password: string, displayName?: string) => {
      const token = await authApi.register({ email, password, displayName })
      setAccessToken(token.accessToken)
      queryClient.clear()
      setUser(token.user)
      setStatus('authed')
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setAccessToken(null)
      setUser(null)
      setStatus('anon')
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, login, register, logout }),
    [user, status, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- hook co-located with its provider
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
