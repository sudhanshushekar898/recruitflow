import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Alert, Button, Card, Pagination, Spinner, TextField } from '../../components/ui'
import { api } from '../../lib/apiClient'
import type { PageResponse, Role, UserView } from '../../types'

const ROLES: Role[] = ['ADMIN', 'RECRUITER', 'INTERVIEWER']
const PAGE_SIZE = 10

/** Read-only directory for now; invites and role changes arrive with the admin forms in a later milestone. */
export function UsersPage() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [role, setRole] = useState<Role | ''>('')

  const { data, isPending, isError, error, isFetching } = useQuery({
    queryKey: ['users', { page, q, role }],
    queryFn: ({ signal }) =>
      api<PageResponse<UserView>>('/api/v1/users', {
        query: { page, size: PAGE_SIZE, q, role, sort: 'lastName,asc' },
        signal,
      }),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Users</h1>
      <Card>
        <form
          className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            setQ(search.trim())
          }}
        >
          <TextField label="Search" className="flex-1" value={search} onChange={(e) => setSearch(e.target.value)} />
          <div>
            <label htmlFor="role-filter" className="block text-sm font-medium text-slate-700">
              Role
            </label>
            <select
              id="role-filter"
              value={role}
              onChange={(e) => {
                setPage(0)
                setRole(e.target.value as Role | '')
              }}
              className="mt-1 block w-full rounded-md border-0 px-3 py-2 ring-1 ring-slate-300"
            >
              <option value="">All roles</option>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </div>
          <Button type="submit">Search</Button>
        </form>

        {isPending && <Spinner />}
        {isError && <Alert>{error instanceof Error ? error.message : 'Could not load users.'}</Alert>}
        {data && data.content.length === 0 && <p className="py-6 text-center text-slate-500">No users match your search.</p>}
        {data && data.content.length > 0 && (
          <div className={`overflow-x-auto ${isFetching ? 'opacity-60' : ''}`}>
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-slate-500">
                <tr>
                  <th className="py-2 pr-4 font-medium">Name</th>
                  <th className="py-2 pr-4 font-medium">Email</th>
                  <th className="py-2 pr-4 font-medium">Roles</th>
                  <th className="py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((user) => (
                  <tr key={user.id}>
                    <td className="py-2 pr-4">
                      {user.firstName} {user.lastName}
                    </td>
                    <td className="py-2 pr-4">{user.email}</td>
                    <td className="py-2 pr-4">{user.roles.join(', ')}</td>
                    <td className="py-2">{!user.enabled ? 'Deactivated' : user.pendingInvite ? 'Invite pending' : 'Active'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
      </Card>
    </div>
  )
}
