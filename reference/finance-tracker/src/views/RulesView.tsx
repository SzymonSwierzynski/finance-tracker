import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { createRule, deleteRule, useCategories, useRules } from '@/data'
import type { Category } from '@/data'

const inputCls =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-1 focus:ring-slate-500'

export function RulesView() {
  const rules = useRules()
  const categories = useCategories()

  const [pattern, setPattern] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [priority, setPriority] = useState('0')
  const [error, setError] = useState<string | null>(null)

  const catById = useMemo(() => {
    const m = new Map<string, Category>()
    for (const c of categories ?? []) m.set(c.id, c)
    return m
  }, [categories])

  const label = (c: Category) =>
    c.parentId ? `${catById.get(c.parentId)?.name ?? '?'} → ${c.name}` : c.name

  const orderedCats = useMemo(
    () =>
      [...(categories ?? [])]
        .map((c) => ({ c, text: label(c) }))
        .sort((a, b) => a.text.localeCompare(b.text)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [categories],
  )

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!pattern.trim()) {
      setError('Enter a text pattern to match.')
      return
    }
    if (!categoryId) {
      setError('Pick a category.')
      return
    }
    await createRule({ pattern, categoryId, priority: Number(priority) || 0 })
    setPattern('')
    setPriority('0')
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <form onSubmit={submit} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Add rule</h2>
        <div className="space-y-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-600">If description contains</span>
            <input
              value={pattern}
              onChange={(e) => setPattern(e.target.value)}
              placeholder="e.g. Biedronka"
              className={inputCls}
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-600">Categorize as</span>
            <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className={inputCls}>
              <option value="">— Choose category —</option>
              {orderedCats.map(({ c, text }) => (
                <option key={c.id} value={c.id}>
                  {text}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs font-medium text-slate-600">Priority (higher wins)</span>
            <input
              type="number"
              value={priority}
              onChange={(e) => setPriority(e.target.value)}
              className={inputCls}
            />
          </label>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button
            type="submit"
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            Add rule
          </button>
          <p className="text-xs text-slate-400">
            Rules auto-categorize imported transactions whose description contains the pattern
            (case-insensitive).
          </p>
        </div>
      </form>

      <div className="rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
        <h2 className="px-2 py-2 text-sm font-semibold text-slate-700">Rules</h2>
        {!rules ? (
          <p className="px-2 text-sm text-slate-500">Loading…</p>
        ) : rules.length === 0 ? (
          <p className="px-2 pb-2 text-sm text-slate-500">No rules yet.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {rules.map((r) => {
              const c = catById.get(r.categoryId)
              return (
                <li key={r.id} className="flex items-center justify-between gap-3 px-2 py-2">
                  <div className="min-w-0">
                    <div className="truncate text-sm text-slate-800">
                      “{r.pattern}” → {c ? label(c) : <span className="text-slate-400">(deleted category)</span>}
                    </div>
                    <div className="text-xs text-slate-400">priority {r.priority}</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => deleteRule(r.id)}
                    className="shrink-0 rounded-md px-2 py-1 text-xs text-slate-400 hover:bg-red-50 hover:text-red-600"
                  >
                    Delete
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </div>
  )
}
