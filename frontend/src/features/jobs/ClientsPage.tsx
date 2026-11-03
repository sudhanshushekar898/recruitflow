import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Button, Card, Spinner, TextField } from '../../components/ui'
import { useClients, useJobMutations } from './api'

export function ClientsPage() {
  const { data, isPending } = useClients()
  const { createClient } = useJobMutations()
  const [name, setName] = useState('')
  const [industry, setIndustry] = useState('')

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Clients</h1>
        <Link to="/jobs" className="text-sm text-indigo-700 hover:underline">← Jobs</Link>
      </div>
      <Card title="Add a client">
        <form
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault()
            if (name.trim()) createClient.mutate({ name: name.trim(), industry: industry.trim() || undefined }, { onSuccess: () => { setName(''); setIndustry('') } })
          }}
        >
          <TextField label="Company name" className="flex-1" value={name} onChange={(e) => setName(e.target.value)} />
          <TextField label="Industry" className="flex-1" value={industry} onChange={(e) => setIndustry(e.target.value)} />
          <Button type="submit" loading={createClient.isPending} disabled={!name.trim()}>Add client</Button>
        </form>
        {createClient.isError && <div className="mt-3"><Alert>{createClient.error.message}</Alert></div>}
      </Card>
      <Card>
        {isPending && <Spinner />}
        {data && (
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-slate-500">
              <tr><th className="py-2 pr-4 font-medium">Name</th><th className="py-2 pr-4 font-medium">Industry</th><th className="py-2 font-medium">Contact</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((c) => (
                <tr key={c.id}>
                  <td className="py-2 pr-4 font-medium">{c.name}</td>
                  <td className="py-2 pr-4">{c.industry ?? '—'}</td>
                  <td className="py-2">{c.contactEmail ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
