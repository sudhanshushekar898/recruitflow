import { Card } from '../components/ui'
import { useAuth } from '../features/auth/useAuth'

/** Placeholder until the analytics milestone; shows what the signed-in role can expect to find here. */
export function DashboardPage() {
  const { user } = useAuth()
  const firstName = user?.name.split(' ')[0]

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Welcome{firstName ? `, ${firstName}` : ''}</h1>
      <Card>
        <p className="text-slate-600">
          Candidates, jobs, the hiring pipeline and interviews will appear here as they are built.
          {user?.roles.includes('ADMIN') && ' As an administrator you can already review users from the sidebar.'}
        </p>
      </Card>
    </div>
  )
}

export function NotFoundPage() {
  return (
    <div className="py-16 text-center">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-slate-600">The page you are looking for does not exist.</p>
    </div>
  )
}
