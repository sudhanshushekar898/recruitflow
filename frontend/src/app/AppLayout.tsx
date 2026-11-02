import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { Button } from '../components/ui'
import { useAuth } from '../features/auth/useAuth'
import { visibleNav } from './navigation'

export function AppLayout() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const items = visibleNav(user?.roles ?? [])

  const link = ({ isActive }: { isActive: boolean }) =>
    `block rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700 hover:bg-slate-100'}`

  return (
    <div className="min-h-screen md:flex">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:hidden">
        <span className="font-semibold text-indigo-600">RecruitFlow</span>
        <Button variant="secondary" aria-expanded={open} aria-controls="sidebar" onClick={() => setOpen(!open)}>
          Menu
        </Button>
      </header>

      <aside
        id="sidebar"
        className={`${open ? 'block' : 'hidden'} border-r border-slate-200 bg-white p-4 md:block md:w-60 md:shrink-0`}
      >
        <p className="mb-6 hidden px-3 text-lg font-semibold text-indigo-600 md:block">RecruitFlow</p>
        <nav aria-label="Main" className="space-y-1">
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'} className={link} onClick={() => setOpen(false)}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-8 border-t border-slate-200 pt-4">
          <p className="truncate px-3 text-sm font-medium">{user?.name}</p>
          <p className="truncate px-3 text-xs text-slate-500">{user?.roles.join(', ')}</p>
          <Button variant="secondary" className="mt-3 w-full" onClick={() => void logout()}>
            Sign out
          </Button>
        </div>
      </aside>

      <main className="flex-1 px-4 py-6 md:px-8">
        <Outlet />
      </main>
    </div>
  )
}
