import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react'
import { forwardRef, useId } from 'react'

export function Button({
  variant = 'primary',
  loading = false,
  className = '',
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary'; loading?: boolean }) {
  const styles =
    variant === 'primary'
      ? 'bg-indigo-600 text-white hover:bg-indigo-700 focus-visible:outline-indigo-600'
      : 'bg-white text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50 focus-visible:outline-indigo-600'
  return (
    <button
      className={`inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-60 ${styles} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? 'Please wait…' : children}
    </button>
  )
}

export const TextField = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }
>(function TextField({ label, error, hint, className = '', ...rest }, ref) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        ref={ref}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className="mt-1 block w-full rounded-md border-0 px-3 py-2 text-slate-900 shadow-sm ring-1 ring-slate-300 focus:ring-2 focus:ring-indigo-600 aria-[invalid=true]:ring-red-500"
        {...rest}
      />
      {hint && !error && (
        <p id={`${id}-hint`} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
})

export function SelectField({
  label,
  children,
  className = '',
  ...rest
}: Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> & { label: string; className?: string }) {
  const id = useId()
  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <select id={id} className="mt-1 block w-full rounded-md border-0 bg-white px-3 py-2 ring-1 ring-slate-300 focus:ring-2 focus:ring-indigo-600" {...rest}>
        {children}
      </select>
    </div>
  )
}

export const TextAreaField = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string }
>(function TextAreaField({ label, error, className = '', ...rest }, ref) {
  const id = useId()
  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <textarea
        id={id}
        ref={ref}
        rows={4}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className="mt-1 block w-full rounded-md border-0 px-3 py-2 ring-1 ring-slate-300 focus:ring-2 focus:ring-indigo-600 aria-[invalid=true]:ring-red-500"
        {...rest}
      />
      {error && (
        <p id={`${id}-error`} className="mt-1 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
})

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'green' | 'amber' | 'red'; children: ReactNode }) {
  const styles = {
    neutral: 'bg-slate-100 text-slate-700',
    green: 'bg-green-100 text-green-800',
    amber: 'bg-amber-100 text-amber-800',
    red: 'bg-red-100 text-red-800',
  }[tone]
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${styles}`}>{children}</span>
}

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'success' | 'info'; children: ReactNode }) {
  const styles = {
    error: 'bg-red-50 text-red-800 ring-red-200',
    success: 'bg-green-50 text-green-800 ring-green-200',
    info: 'bg-blue-50 text-blue-800 ring-blue-200',
  }[tone]
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-md p-3 text-sm ring-1 ${styles}`}>
      {children}
    </div>
  )
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" aria-label={label} className="flex justify-center p-8">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-indigo-600" />
    </div>
  )
}

export function Card({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="rounded-lg bg-white p-6 shadow-sm ring-1 ring-slate-200">
      {title && <h2 className="mb-4 text-lg font-semibold">{title}</h2>}
      {children}
    </section>
  )
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-between">
      <Button variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span className="text-sm text-slate-600">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next
      </Button>
    </nav>
  )
}
