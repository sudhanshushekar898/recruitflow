import { LogOut, Menu, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { Avatar } from '../components/ui'
import { useAuth } from '../features/auth/useAuth'
import { visibleNav } from './navigation'

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <span className="flex items-center gap-2.5">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 text-sm font-bold text-white shadow-sm">R</span>
      <span className={`text-lg font-bold tracking-tight ${light ? 'text-white' : 'text-slate-900'}`}>RecruitFlow</span>
    </span>
  )
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const items = visibleNav(user?.roles ?? [])
  const role = user?.roles.map((r) => r.charAt(0) + r.slice(1).toLowerCase()).join(', ')

  const sidebar = (
    <div className="flex h-full flex-col bg-slate-900 px-4 py-5">
      <div className="hidden px-2 md:block">
        <Logo light />
      </div>
      <nav aria-label="Main" className="mt-8 flex-1 space-y-1">
        {items.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            onClick={() => setOpen(false)}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                isActive ? 'bg-white/10 text-white' : 'text-slate-400 hover:bg-white/5 hover:text-white'
              }`
            }
          >
            <Icon className="h-[18px] w-[18px]" aria-hidden />
            {label}
          </NavLink>
        ))}
      </nav>
      <div className="mt-6 rounded-xl bg-white/5 p-3">
        <div className="flex items-center gap-3">
          <Avatar name={user?.name} />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-white">{user?.name}</p>
            <p className="truncate text-xs text-slate-400">{role}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void logout()}
          className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium text-slate-300 ring-1 ring-white/10 transition hover:bg-white/10 hover:text-white"
        >
          <LogOut className="h-4 w-4" aria-hidden />
          Sign out
        </button>
      </div>
    </div>
  )

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-20 flex items-center justify-between border-b border-slate-200 bg-white/90 px-4 py-3 backdrop-blur md:hidden">
        <Logo />
        <button
          type="button"
          aria-expanded={open}
          aria-controls="sidebar"
          aria-label={open ? 'Close menu' : 'Menu'}
          onClick={() => setOpen(!open)}
          className="rounded-lg p-2 text-slate-600 hover:bg-slate-100"
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </header>

      <aside id="sidebar" className={`${open ? 'block' : 'hidden'} fixed inset-y-0 left-0 z-30 w-64 md:block`}>
        {sidebar}
      </aside>
      {open && <div className="fixed inset-0 z-20 bg-slate-900/40 md:hidden" onClick={() => setOpen(false)} aria-hidden />}

      <main className="md:pl-64">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
