/**
 * Theme (light/dark) store, shared across the app so every consumer — the header toggle AND the
 * charts — re-renders together on a flip. Mirrors lib/i18n's storage pattern (guarded localStorage
 * + system fallback); the initial `.dark` class is set pre-paint by an inline script in index.html.
 */
import { useSyncExternalStore } from 'react'

export type Theme = 'light' | 'dark'
const STORAGE_KEY = 'ft-theme'

function readStored(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

function systemPrefersDark(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches
}

/** Stored preference if set, else the OS preference. Matches the index.html boot script. */
export function resolveTheme(): Theme {
  return readStored() ?? (systemPrefersDark() ? 'dark' : 'light')
}

let current: Theme = typeof document === 'undefined' ? 'light' : resolveTheme()
const listeners = new Set<() => void>()

function setTheme(theme: Theme): void {
  current = theme
  document.documentElement.classList.toggle('dark', theme === 'dark')
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    /* storage unavailable — the class is still applied for this session */
  }
  listeners.forEach((notify) => notify())
}

function toggleTheme(): void {
  setTheme(current === 'dark' ? 'light' : 'dark')
}

/** Shared subscription so all useTheme() consumers update together on a toggle. */
export function useTheme(): { theme: Theme; toggle: () => void } {
  const theme = useSyncExternalStore(
    (notify) => {
      listeners.add(notify)
      return () => listeners.delete(notify)
    },
    () => current,
    () => current,
  )
  return { theme, toggle: toggleTheme }
}

/** Recharts colours resolved per theme (SVG presentation attrs can't read CSS vars reliably). */
export function chartColors(theme: Theme): { grid: string; axis: string; surface: string } {
  const dark = theme === 'dark'
  return {
    grid: dark ? '#1e293b' : '#e2e8f0',
    axis: dark ? '#94a3b8' : '#64748b',
    surface: dark ? '#0f172a' : '#ffffff',
  }
}
