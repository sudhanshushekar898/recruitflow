import type { Role } from '../types'

export interface NavItem {
  to: string
  label: string
  /** Omit for items every signed-in user can see. */
  roles?: Role[]
}

/** Each milestone adds its entries here; the sidebar and route guards both read this list. */
export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/jobs', label: 'Jobs', roles: ['ADMIN', 'RECRUITER'] },
  { to: '/candidates', label: 'Candidates', roles: ['ADMIN', 'RECRUITER'] },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
]

export function visibleNav(userRoles: Role[]): NavItem[] {
  return NAV_ITEMS.filter((item) => !item.roles || item.roles.some((role) => userRoles.includes(role)))
}
