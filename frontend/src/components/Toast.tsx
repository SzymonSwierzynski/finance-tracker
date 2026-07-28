import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

type ToastTone = 'success' | 'error' | 'info'
interface ToastAction {
  label: string
  onClick: () => void
}
interface Toast {
  id: number
  tone: ToastTone
  message: string
  action?: ToastAction
}

interface ToastApi {
  success: (message: string) => void
  error: (message: string) => void
  info: (message: string) => void
  action: (message: string, actionLabel: string, onAction: () => void) => void
}

const ToastContext = createContext<ToastApi | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const remove = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), [])

  const push = useCallback(
    (tone: ToastTone, message: string, action?: ToastAction) => {
      const id = nextId.current++
      setToasts((t) => [...t, { id, tone, message, action }])
      setTimeout(() => remove(id), 4500)
    },
    [remove],
  )

  const api = useMemo<ToastApi>(
    () => ({
      success: (m) => push('success', m),
      error: (m) => push('error', m),
      info: (m) => push('info', m),
      action: (m, label, onAction) => push('info', m, { label, onClick: onAction }),
    }),
    [push],
  )

  const tones: Record<ToastTone, string> = {
    success: 'border-positive/30 bg-positive-soft text-positive',
    error: 'border-negative/30 bg-negative-soft text-negative',
    info: 'border-border bg-surface text-fg-muted',
  }

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex flex-col items-center gap-2 px-4" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`pointer-events-auto w-full max-w-sm rounded-lg border px-4 py-3 text-sm font-medium shadow-lg ${tones[t.tone]}`}
            onClick={() => remove(t.id)}
          >
            <span className="flex items-center justify-between gap-3">
              {t.message}
              {t.action && (
                <button
                  type="button"
                  className="shrink-0 font-semibold underline underline-offset-2"
                  onClick={(e) => {
                    e.stopPropagation()
                    t.action?.onClick()
                    remove(t.id)
                  }}
                >
                  {t.action.label}
                </button>
              )}
            </span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components -- hook co-located with its provider
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used within ToastProvider')
  return ctx
}
