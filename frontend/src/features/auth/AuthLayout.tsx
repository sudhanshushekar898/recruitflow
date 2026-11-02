import type { ReactNode } from 'react'

/** Centered card shared by the signed-out pages. */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <p className="mb-2 text-center text-sm font-semibold uppercase tracking-wide text-indigo-600">RecruitFlow</p>
        <h1 className="mb-6 text-center text-2xl font-semibold">{title}</h1>
        <div className="rounded-lg bg-white p-6 shadow-sm ring-1 ring-slate-200">{children}</div>
      </div>
    </main>
  )
}
