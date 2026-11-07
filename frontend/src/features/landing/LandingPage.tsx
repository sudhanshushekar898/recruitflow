import {
  ArrowRight,
  BarChart3,
  CalendarCheck,
  CheckCircle2,
  Clock,
  EyeOff,
  KanbanSquare,
  Lock,
  Search,
  ShieldCheck,
  Sparkles,
  Users,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { Logo } from '../../app/AppLayout'
import { Avatar } from '../../components/ui'

const FEATURES = [
  { icon: KanbanSquare, title: 'Visual hiring pipeline', text: 'Drag candidates from New to Hired. Only legal moves are allowed, and every step is recorded with who made it and why.' },
  { icon: Search, title: 'Find the right people fast', text: 'Search the talent pool by keyword, location, experience and must-have skills, with aliases like k8s or postgres understood.' },
  { icon: CalendarCheck, title: 'Interviews without clashes', text: 'Book panels in seconds. RecruitFlow refuses double-bookings, even when two recruiters click at the same moment.' },
  { icon: EyeOff, title: 'Blind, structured feedback', text: 'Ratings and recommendations stay hidden from colleagues until each interviewer submits their own, so nobody anchors.' },
  { icon: ShieldCheck, title: 'Privacy built in', text: 'Consent, retention dates and an append-only audit trail for candidate data. Interviewers never see contact details.' },
  { icon: BarChart3, title: 'See where hiring stalls', text: 'A live hiring funnel, stale-candidate flags and recent activity show exactly where each job stands.' },
]

const STEPS = [
  { n: '1', title: 'Open a job', text: 'Add the client, salary and the skills that matter, then open it for applications.' },
  { n: '2', title: 'Build the shortlist', text: 'Add candidates from your talent pool and move them through screening on the board.' },
  { n: '3', title: 'Interview and decide', text: 'Schedule panels, collect blind feedback, make the offer, and record the hire.' },
]

/** A small, static picture of the product: three pipeline columns with candidate cards. */
function BoardPreview() {
  const columns = [
    { title: 'Screening', accent: 'border-t-sky-500', cards: [['Priya Sharma', 'Senior Java Developer', '2d'], ['Liam Okafor', 'Backend Engineer', '5d']] },
    { title: 'Interview', accent: 'border-t-violet-500', cards: [['Mei Tanaka', 'Platform Engineer', '1d'], ['Omar Haddad', 'Data Engineer', '3d']] },
    { title: 'Offer', accent: 'border-t-amber-500', cards: [['Sofia Silva', 'Full Stack Developer', 'today']] },
  ]
  return (
    <div className="relative rounded-2xl bg-white/70 p-3 shadow-2xl ring-1 ring-slate-900/10 backdrop-blur" aria-hidden>
      <div className="flex items-center gap-1.5 px-2 pb-3">
        <span className="h-2.5 w-2.5 rounded-full bg-rose-400" />
        <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
        <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
        <span className="ml-3 text-xs font-medium text-slate-500">Senior Java Engineer · Northwind Fintech</span>
      </div>
      <div className="grid grid-cols-3 gap-3">
        {columns.map((col) => (
          <div key={col.title} className={`rounded-xl border-t-4 ${col.accent} bg-slate-100/80 p-2.5`}>
            <p className="mb-2 flex items-center justify-between px-1 text-xs font-semibold text-slate-700">
              {col.title}
              <span className="rounded-full bg-white px-1.5 text-[10px] shadow-sm">{col.cards.length}</span>
            </p>
            <div className="space-y-2">
              {col.cards.map(([name, title, days]) => (
                <div key={name} className="rounded-lg bg-white p-2.5 shadow-sm ring-1 ring-slate-200">
                  <div className="flex items-center gap-2">
                    <Avatar name={name} size="sm" />
                    <div className="min-w-0">
                      <p className="truncate text-xs font-semibold text-slate-900">{name}</p>
                      <p className="truncate text-[10px] text-slate-500">{title}</p>
                    </div>
                  </div>
                  <p className="mt-2 flex items-center gap-1 text-[10px] text-slate-400"><Clock className="h-2.5 w-2.5" />{days}</p>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="absolute -bottom-6 -left-6 hidden items-center gap-3 rounded-xl bg-white p-3 shadow-xl ring-1 ring-slate-200 sm:flex">
        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600"><CheckCircle2 className="h-5 w-5" /></span>
        <div>
          <p className="text-xs font-semibold text-slate-900">Sofia Silva hired</p>
          <p className="text-[10px] text-slate-500">2 of 2 openings filled</p>
        </div>
      </div>
      <div className="absolute -right-5 -top-5 hidden items-center gap-2 rounded-xl bg-white p-3 shadow-xl ring-1 ring-slate-200 sm:flex">
        <span className="flex">{[1, 2, 3, 4, 5].map((n) => <span key={n} className={`text-sm ${n <= 4 ? 'text-amber-400' : 'text-slate-200'}`}>★</span>)}</span>
        <span className="text-[10px] font-medium text-slate-600">Feedback in</span>
      </div>
    </div>
  )
}

/** Public homepage shown to visitors and after signing out. */
export function LandingPage() {
  return (
    <div className="min-h-screen bg-white">
      <header className="sticky top-0 z-30 border-b border-slate-200/70 bg-white/80 backdrop-blur">
        <nav aria-label="Site" className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8">
          <Logo />
          <div className="flex items-center gap-6">
            <a href="#features" className="hidden text-sm font-medium text-slate-600 hover:text-slate-900 sm:block">Features</a>
            <a href="#how" className="hidden text-sm font-medium text-slate-600 hover:text-slate-900 sm:block">How it works</a>
            <Link to="/login" className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-700">Sign in</Link>
          </div>
        </nav>
      </header>

      <main>
        <section className="relative overflow-hidden">
          <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(60rem_30rem_at_70%_-10%,rgba(99,102,241,0.18),transparent)]" aria-hidden />
          <div className="relative mx-auto grid max-w-7xl items-center gap-16 px-4 py-20 sm:px-6 lg:grid-cols-2 lg:px-8 lg:py-28">
            <div>
              <span className="inline-flex items-center gap-2 rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700 ring-1 ring-inset ring-indigo-600/20">
                <Sparkles className="h-3.5 w-3.5" /> Built for recruitment agencies
              </span>
              <h1 className="mt-6 text-4xl font-extrabold tracking-tight text-slate-900 sm:text-5xl lg:text-6xl">
                Hire faster, with every candidate <span className="bg-gradient-to-r from-indigo-600 to-violet-600 bg-clip-text text-transparent">in view.</span>
              </h1>
              <p className="mt-6 max-w-xl text-lg leading-relaxed text-slate-600">
                RecruitFlow brings your candidates, client jobs, hiring pipeline and interviews into one workspace,
                so your team always knows who is where and what happens next.
              </p>
              <div className="mt-10 flex flex-wrap items-center gap-4">
                <Link to="/login" className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-3 text-sm font-semibold text-white shadow-lg shadow-indigo-600/25 transition hover:bg-indigo-500">
                  Sign in to your workspace <ArrowRight className="h-4 w-4" />
                </Link>
                <a href="#features" className="text-sm font-semibold text-slate-700 hover:text-indigo-600">See what it does →</a>
              </div>
              <dl className="mt-12 grid max-w-md grid-cols-3 gap-6 border-t border-slate-200 pt-8">
                {[['8', 'pipeline stages'], ['0', 'double-bookings'], ['100%', 'moves audited']].map(([value, label]) => (
                  <div key={label}>
                    <dt className="text-2xl font-bold text-slate-900">{value}</dt>
                    <dd className="text-xs text-slate-500">{label}</dd>
                  </div>
                ))}
              </dl>
            </div>
            <div className="lg:pl-6"><BoardPreview /></div>
          </div>
        </section>

        <section id="features" className="scroll-mt-20 bg-slate-50 py-24">
          <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
            <div className="mx-auto max-w-2xl text-center">
              <p className="text-sm font-semibold uppercase tracking-wide text-indigo-600">Everything in one place</p>
              <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">From first contact to signed offer</h2>
              <p className="mt-4 text-slate-600">The tools a small agency team needs every day, without spreadsheets or lost emails.</p>
            </div>
            <div className="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {FEATURES.map(({ icon: Icon, title, text }) => (
                <div key={title} className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200/70 transition hover:-translate-y-0.5 hover:shadow-md">
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-sm">
                    <Icon className="h-5 w-5" />
                  </span>
                  <h3 className="mt-5 text-base font-semibold text-slate-900">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-slate-600">{text}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section id="how" className="scroll-mt-20 py-24">
          <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
            <div className="mx-auto max-w-2xl text-center">
              <p className="text-sm font-semibold uppercase tracking-wide text-indigo-600">How it works</p>
              <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">Three steps to a hire</h2>
            </div>
            <ol className="mt-16 grid gap-8 md:grid-cols-3">
              {STEPS.map((s) => (
                <li key={s.n} className="relative rounded-2xl border border-slate-200 p-8">
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-indigo-600 text-sm font-bold text-white">{s.n}</span>
                  <h3 className="mt-5 text-lg font-semibold text-slate-900">{s.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-slate-600">{s.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="px-4 pb-24 sm:px-6 lg:px-8">
          <div className="relative mx-auto max-w-7xl overflow-hidden rounded-3xl bg-gradient-to-br from-indigo-600 via-indigo-700 to-violet-800 px-8 py-16 text-center shadow-xl sm:px-16">
            <div className="pointer-events-none absolute -right-20 -top-20 h-72 w-72 rounded-full bg-white/10 blur-3xl" aria-hidden />
            <div className="relative mx-auto max-w-2xl">
              <div className="flex justify-center gap-6 text-indigo-200">
                <span className="inline-flex items-center gap-1.5 text-xs font-medium"><Users className="h-4 w-4" />Recruiters</span>
                <span className="inline-flex items-center gap-1.5 text-xs font-medium"><CalendarCheck className="h-4 w-4" />Interviewers</span>
                <span className="inline-flex items-center gap-1.5 text-xs font-medium"><Lock className="h-4 w-4" />Admins</span>
              </div>
              <h2 className="mt-6 text-3xl font-bold tracking-tight text-white sm:text-4xl">Ready to see your pipeline?</h2>
              <p className="mt-4 text-indigo-100">Sign in with the account your administrator created for you.</p>
              <Link to="/login" className="mt-8 inline-flex items-center gap-2 rounded-lg bg-white px-6 py-3 text-sm font-semibold text-indigo-700 shadow-lg transition hover:bg-indigo-50">
                Sign in <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-200 py-10">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <Logo />
          <p className="text-sm text-slate-500">Recruitment operations and applicant tracking · Synthetic demo data only</p>
        </div>
      </footer>
    </div>
  )
}
