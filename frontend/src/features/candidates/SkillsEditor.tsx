import { SkillPicker } from '../skills/SkillPicker'
import type { SkillLevel } from '../../types'
import type { SkillDraft } from './api'
import { LEVELS } from './labels'

/** Edits a candidate's skills: add from the catalog, then set years and level per skill. */
export function SkillsEditor({ value, onChange }: { value: SkillDraft[]; onChange: (next: SkillDraft[]) => void }) {
  const update = (skillId: string, patch: Partial<SkillDraft>) =>
    onChange(value.map((s) => (s.skillId === skillId ? { ...s, ...patch } : s)))

  return (
    <div className="space-y-3">
      <SkillPicker
        label="Skills"
        excludeIds={value.map((s) => s.skillId)}
        onPick={(skill) => onChange([...value, { skillId: skill.id, displayName: skill.displayName }])}
      />
      {value.length === 0 && <p className="text-sm text-slate-500">No skills added yet.</p>}
      <ul className="space-y-2">
        {value.map((skill) => (
          <li key={skill.skillId} className="flex flex-wrap items-center gap-2 rounded-md bg-slate-50 p-2">
            <span className="min-w-24 flex-1 text-sm font-medium">{skill.displayName}</span>
            <label className="text-xs text-slate-600">
              <span className="sr-only">Years of {skill.displayName}</span>
              <input
                type="number"
                min={0}
                max={60}
                placeholder="Years"
                value={skill.years ?? ''}
                onChange={(e) => update(skill.skillId, { years: e.target.value === '' ? undefined : Number(e.target.value) })}
                className="w-20 rounded-md border-0 px-2 py-1 text-sm ring-1 ring-slate-300"
              />
            </label>
            <label className="text-xs text-slate-600">
              <span className="sr-only">Level of {skill.displayName}</span>
              <select
                value={skill.level ?? ''}
                onChange={(e) => update(skill.skillId, { level: (e.target.value || undefined) as SkillLevel | undefined })}
                className="rounded-md border-0 bg-white px-2 py-1 text-sm ring-1 ring-slate-300"
              >
                <option value="">Level</option>
                {Object.entries(LEVELS).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              onClick={() => onChange(value.filter((s) => s.skillId !== skill.skillId))}
              className="text-sm text-red-600 hover:underline"
              aria-label={`Remove ${skill.displayName}`}
            >
              Remove
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
