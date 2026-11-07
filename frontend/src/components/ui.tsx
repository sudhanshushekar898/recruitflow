import type { LucideIcon } from 'lucide-react'
import { ChevronLeft, ChevronRight, CircleAlert, CircleCheck, Info, Loader2 } from 'lucide-react'
import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react'
import { forwardRef, useId } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-indigo-600 text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-indigo-600',
  secondary: 'bg-white text-slate-700 shadow-sm ring-1 ring-inset ring-slate-300 hover:bg-slate-50 focus-visible:outline-indigo-600',
  ghost: 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-indigo-600',
  danger: 'bg-red-600 text-white shadow-sm hover:bg-red-500 focus-visible:outline-red-600',
}

export function Button({
  variant = 'primary',
  loading = false,
  icon: Icon,
  className = '',
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean; icon?: LucideIcon }) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${VARIANTS[variant]} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : Icon && <Icon className="h-4 w-4" aria-hidden />}
      {loading ? 'Please wait…' : children}
    </button>
  )
}

/** Same look as Button for router links (pass the link element's props through `as`-free composition). */
export const linkButton = (variant: Variant = 'primary') =>
  `inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 ${VARIANTS[variant]}`

const FIELD =
  'mt-1.5 block w-full rounded-lg border-0 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 aria-[invalid=true]:ring-red-500'

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
      <input id={id} ref={ref} aria-invalid={error ? true : undefined} aria-describedby={describedBy} className={FIELD} {...rest} />
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
      <select id={id} className={FIELD} {...rest}>
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
        className={FIELD}
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

export type Tone = 'neutral' | 'green' | 'amber' | 'red' | 'blue' | 'violet'

const TONES: Record<Tone, { pill: string; dot: string }> = {
  neutral: { pill: 'bg-slate-100 text-slate-700 ring-slate-500/10', dot: 'bg-slate-400' },
  green: { pill: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', dot: 'bg-emerald-500' },
  amber: { pill: 'bg-amber-50 text-amber-800 ring-amber-600/20', dot: 'bg-amber-500' },
  red: { pill: 'bg-rose-50 text-rose-700 ring-rose-600/20', dot: 'bg-rose-500' },
  blue: { pill: 'bg-sky-50 text-sky-700 ring-sky-600/20', dot: 'bg-sky-500' },
  violet: { pill: 'bg-violet-50 text-violet-700 ring-violet-600/20', dot: 'bg-violet-500' },
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${TONES[tone].pill}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${TONES[tone].dot}`} aria-hidden />
      {children}
    </span>
  )
}

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'success' | 'info'; children: ReactNode }) {
  const styles = {
    error: { box: 'bg-rose-50 text-rose-800 ring-rose-200', Icon: CircleAlert },
    success: { box: 'bg-emerald-50 text-emerald-800 ring-emerald-200', Icon: CircleCheck },
    info: { box: 'bg-sky-50 text-sky-800 ring-sky-200', Icon: Info },
  }[tone]
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`flex items-start gap-2.5 rounded-lg p-3 text-sm ring-1 ${styles.box}`}>
      <styles.Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  )
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" aria-label={label} className="flex justify-center p-10">
      <Loader2 className="h-7 w-7 animate-spin text-indigo-600" aria-hidden />
    </div>
  )
}

export function Card({
  title,
  description,
  action,
  children,
  padded = true,
}: {
  title?: string
  description?: string
  action?: ReactNode
  children: ReactNode
  padded?: boolean
}) {
  return (
    <section className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200/70">
      {(title || action) && (
        <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div>
            {title && <h2 className="text-base font-semibold text-slate-900">{title}</h2>}
            {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
          </div>
          {action}
        </div>
      )}
      <div className={padded ? 'p-5' : ''}>{children}</div>
    </section>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
        {subtitle && <div className="mt-1 text-sm text-slate-500">{subtitle}</div>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

const AVATAR_COLORS = [
  'bg-indigo-100 text-indigo-700',
  'bg-emerald-100 text-emerald-700',
  'bg-amber-100 text-amber-800',
  'bg-rose-100 text-rose-700',
  'bg-sky-100 text-sky-700',
  'bg-violet-100 text-violet-700',
  'bg-teal-100 text-teal-700',
  'bg-orange-100 text-orange-700',
]

/** Initials on a colour picked from the name, so the same person always gets the same colour. */
export function Avatar({ name, size = 'md' }: { name?: string; size?: 'sm' | 'md' | 'lg' }) {
  const safe = name?.trim() || '?'
  const initials = safe.split(/\s+/).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('')
  const hash = [...safe].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7)
  const dims = { sm: 'h-7 w-7 text-[11px]', md: 'h-9 w-9 text-xs', lg: 'h-12 w-12 text-base' }[size]
  return (
    <span aria-hidden className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold ${dims} ${AVATAR_COLORS[hash % AVATAR_COLORS.length]}`}>
      {initials}
    </span>
  )
}

export function StatCard({ label, value, icon: Icon, tone = 'indigo', hint }: {
  label: string
  value?: number | string
  icon: LucideIcon
  tone?: 'indigo' | 'emerald' | 'amber' | 'violet'
  hint?: string
}) {
  const color = { indigo: 'bg-indigo-50 text-indigo-600', emerald: 'bg-emerald-50 text-emerald-600', amber: 'bg-amber-50 text-amber-600', violet: 'bg-violet-50 text-violet-600' }[tone]
  return (
    <div className="flex items-center gap-4 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200/70 transition hover:shadow-md">
      <span className={`flex h-11 w-11 items-center justify-center rounded-lg ${color}`}>
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div>
        <p className="text-2xl font-bold tracking-tight text-slate-900">{value ?? '–'}</p>
        <p className="text-sm text-slate-500">{label}</p>
        {hint && <p className="text-xs text-slate-400">{hint}</p>}
      </div>
    </div>
  )
}

export function EmptyState({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Icon className="h-6 w-6" aria-hidden />
      </span>
      <p className="mt-3 text-sm font-semibold text-slate-900">{title}</p>
      {children && <div className="mt-1 text-sm text-slate-500">{children}</div>}
    </div>
  )
}

/** Shared table styles so every list looks the same. */
export const table = {
  wrap: 'overflow-x-auto',
  table: 'min-w-full text-left text-sm',
  head: 'bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500',
  th: 'px-5 py-3 font-semibold',
  body: 'divide-y divide-slate-100',
  row: 'transition hover:bg-slate-50/70',
  td: 'px-5 py-3.5 align-middle',
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between border-t border-slate-100 px-5 py-3">
      <Button variant="secondary" icon={ChevronLeft} disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span className="text-sm text-slate-500">
        Page <span className="font-medium text-slate-900">{page + 1}</span> of {totalPages}
      </span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next <ChevronRight className="h-4 w-4" aria-hidden />
      </Button>
    </nav>
  )
}
