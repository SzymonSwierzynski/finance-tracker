import { forwardRef } from 'react'
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-brand-300',
  secondary: 'bg-surface text-fg-muted ring-1 ring-inset ring-border-strong hover:bg-surface-2 disabled:opacity-50',
  ghost: 'text-fg-muted hover:bg-surface-2 disabled:opacity-50',
  danger: 'bg-red-600 text-white hover:bg-red-700 disabled:bg-red-300',
}
const SIZES: Record<Size, string> = { sm: 'px-2.5 py-1.5 text-sm', md: 'px-4 py-2 text-sm' }

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export function Button({ variant = 'primary', size = 'md', loading, disabled, children, className = '', ...rest }: ButtonProps) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:cursor-not-allowed ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && <Spinner className="size-4" />}
      {children}
    </button>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-xl border border-border bg-surface shadow-sm ${className}`}>{children}</div>
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-fg">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-fg-soft">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  )
}

export function Field({ label, htmlFor, error, hint, children }: { label: string; htmlFor?: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="block text-sm font-medium text-fg-muted">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-fg-soft">{hint}</p>}
      {error && <p className="text-xs font-medium text-negative">{error}</p>}
    </div>
  )
}

const inputClass =
  'block w-full rounded-lg border-0 bg-surface px-3 py-2 text-sm text-fg shadow-sm ring-1 ring-inset ring-border-strong placeholder:text-fg-subtle focus:ring-2 focus:ring-inset focus:ring-brand-600'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...rest }, ref) => <input ref={ref} className={`${inputClass} ${className}`} {...rest} />,
)
Input.displayName = 'Input'

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className = '', children, ...rest }, ref) => (
    <select ref={ref} className={`${inputClass} ${className}`} {...rest}>
      {children}
    </select>
  ),
)
Select.displayName = 'Select'

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...rest }, ref) => <textarea ref={ref} className={`${inputClass} ${className}`} {...rest} />,
)
TextArea.displayName = 'TextArea'

export function Spinner({ className = 'size-5' }: { className?: string }) {
  return (
    <svg className={`animate-spin text-current ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-90" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z" />
    </svg>
  )
}

export function Badge({ children, tone = 'slate' }: { children: ReactNode; tone?: 'slate' | 'green' | 'red' | 'indigo' }) {
  const tones = {
    slate: 'bg-surface-2 text-fg-muted',
    green: 'bg-positive-soft text-positive',
    red: 'bg-negative-soft text-negative',
    indigo: 'bg-accent-soft text-accent',
  }
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>
}

export function CenteredState({ icon, title, message, action }: { icon?: ReactNode; title: string; message?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border-strong bg-surface px-6 py-12 text-center">
      {icon && <div className="mb-3 text-fg-subtle">{icon}</div>}
      <p className="text-sm font-medium text-fg-muted">{title}</p>
      {message && <p className="mt-1 max-w-sm text-sm text-fg-soft">{message}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-border ${className}`} />
}
