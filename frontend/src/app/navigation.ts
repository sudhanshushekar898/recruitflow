import type { LucideIcon } from 'lucide-react'
import { Briefcase, CalendarCheck, CalendarDays, LayoutDashboard, ShieldCheck, Users } from 'lucide-react'
import type { Role } from '../types'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  /** Omit for items every signed-in user can see. */
  roles?: Role[]
}

/** Each milestone adds its entries here; the sidebar and route guards both read this list. */
export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/jobs', label: 'Jobs', icon: Briefcase, roles: ['ADMIN', 'RECRUITER'] },
  { to: '/candidates', label: 'Candidates', icon: Users, roles: ['ADMIN', 'RECRUITER'] },
  { to: '/interviews', label: 'Interviews', icon: CalendarDays, roles: ['ADMIN', 'RECRUITER'] },
  { to: '/my-interviews', label: 'My interviews', icon: CalendarCheck },
  { to: '/users', label: 'Users', icon: ShieldCheck, roles: ['ADMIN'] },
]

export function visibleNav(userRoles: Role[]): NavItem[] {
  return NAV_ITEMS.filter((item) => !item.roles || item.roles.some((role) => userRoles.includes(role)))
}
