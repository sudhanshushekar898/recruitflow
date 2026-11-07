import { CalendarCheck, KanbanSquare, ShieldCheck } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from '../../app/AppLayout'

const POINTS = [
  { icon: KanbanSquare, text: 'Move candidates through a visual hiring pipeline' },
  { icon: CalendarCheck, text: 'Schedule interviews and collect structured feedback' },
  { icon: ShieldCheck, text: 'Consent, retention and audit built in for candidate data' },
]

/** Split screen shared by the signed-out pages: brand panel on the left, the form on the right. */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="flex min-h-screen">
      <div className="relative hidden w-[44%] overflow-hidden bg-gradient-to-br from-indigo-600 via-indigo-700 to-violet-800 p-12 text-white lg:flex lg:flex-col">
        <div className="pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" aria-hidden />
        <div className="pointer-events-none absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-violet-400/20 blur-3xl" aria-hidden />
        <Link to="/welcome" className="relative w-fit" aria-label="RecruitFlow home"><Logo light /></Link>
        <div className="relative mt-auto max-w-md">
          <h2 className="text-3xl font-bold leading-tight tracking-tight">Hire faster, with every candidate in view.</h2>
          <p className="mt-4 text-indigo-100">The recruitment workspace for agencies: candidates, jobs, pipelines and interviews in one place.</p>
          <ul className="mt-8 space-y-4">
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-sm text-indigo-50">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/10 ring-1 ring-white/20">
                  <Icon className="h-4 w-4" aria-hidden />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>
        <p className="relative mt-12 text-xs text-indigo-200">© RecruitFlow</p>
      </div>

      <div className="flex flex-1 items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-sm">
          <div className="lg:hidden">
            <Logo />
          </div>
          <h1 className="mt-8 text-2xl font-bold tracking-tight text-slate-900 lg:mt-0">{title}</h1>
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </main>
  )
}
