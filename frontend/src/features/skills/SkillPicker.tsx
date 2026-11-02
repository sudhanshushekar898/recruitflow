import { useQuery } from '@tanstack/react-query'
import { useEffect, useId, useState } from 'react'
import { api } from '../../lib/apiClient'
import type { SkillView } from '../../types'

/** Autocomplete over the skill catalog (names, display names and aliases such as "k8s"). */
export function SkillPicker({
  onPick,
  excludeIds = [],
  label = 'Add a skill',
}: {
  onPick: (skill: SkillView) => void
  excludeIds?: string[]
  label?: string
}) {
  const id = useId()
  const [text, setText] = useState('')
  const [debounced, setDebounced] = useState('')
  const [open, setOpen] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(text.trim()), 200)
    return () => clearTimeout(timer)
  }, [text])

  const { data = [], isFetching } = useQuery({
    queryKey: ['skills', debounced],
    queryFn: ({ signal }) => api<SkillView[]>('/api/v1/skills', { query: { q: debounced }, signal }),
    staleTime: 60_000,
  })
  const options = data.filter((skill) => !excludeIds.includes(skill.id))

  return (
    <div className="relative">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        autoComplete="off"
        value={text}
        placeholder="Search skills, e.g. java or k8s"
        onChange={(e) => {
          setText(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        className="mt-1 block w-full rounded-md border-0 px-3 py-2 ring-1 ring-slate-300 focus:ring-2 focus:ring-indigo-600"
      />
      {open && (
        <ul
          id={`${id}-list`}
          role="listbox"
          aria-label="Matching skills"
          className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 shadow-lg ring-1 ring-slate-200"
        >
          {options.length === 0 && (
            <li className="px-3 py-2 text-sm text-slate-500">{isFetching ? 'Searching…' : 'No matching skills'}</li>
          )}
          {options.map((skill) => (
            <li key={skill.id} role="option" aria-selected={false}>
              <button
                type="button"
                // keep focus in the input so the list does not close before the click registers
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  onPick(skill)
                  setText('')
                  setDebounced('')
                  setOpen(false) // reopens on the next keystroke or focus
                }}
                className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-indigo-50"
              >
                <span>{skill.displayName}</span>
                {skill.category && <span className="text-xs text-slate-500">{skill.category}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
