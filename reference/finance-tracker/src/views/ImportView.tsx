import { useEffect, useMemo, useState } from 'react'
import type { ChangeEvent, ReactNode } from 'react'
import {
  buildImportRows,
  commitImport,
  getImportProfile,
  undoImportBatch,
  useAccounts,
  useCategories,
  useImportBatches,
  useRules,
} from '@/data'
import type { Category, CommitImportResult, ImportMapping, ImportProfile } from '@/data'
import { decodeBuffer, looksMisdecoded, parseCsv, SUPPORTED_ENCODINGS } from '@/lib/csv'
import { DATE_FORMAT_OPTIONS } from '@/lib/csvDate'
import { matchCategory } from '@/lib/rules'
import { formatMinor } from '@/lib/money'

const inputCls =
  'rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-1 focus:ring-slate-500'

const DELIMITERS: { value: string; label: string }[] = [
  { value: '', label: 'Auto' },
  { value: ';', label: 'Semicolon ;' },
  { value: ',', label: 'Comma ,' },
  { value: '\t', label: 'Tab' },
  { value: '|', label: 'Pipe |' },
]

const DEFAULT_MAPPING: ImportMapping = {
  delimiter: '',
  encoding: 'utf-8',
  hasHeader: true,
  dateIndex: 0,
  dateFormat: 'auto',
  descriptionIndex: 1,
  amountMode: 'signed',
  amountIndex: 2,
  expenseIsNegative: true,
  debitIndex: -1,
  creditIndex: -1,
}

function profileToMapping(p: ImportProfile): ImportMapping {
  return {
    delimiter: p.delimiter,
    encoding: p.encoding,
    hasHeader: p.hasHeader,
    dateIndex: p.dateIndex,
    dateFormat: p.dateFormat,
    descriptionIndex: p.descriptionIndex,
    amountMode: p.amountMode,
    amountIndex: p.amountIndex,
    expenseIsNegative: p.expenseIsNegative,
    debitIndex: p.debitIndex,
    creditIndex: p.creditIndex,
  }
}

export function ImportView() {
  const accounts = useAccounts()
  const categories = useCategories()
  const rules = useRules()
  const batches = useImportBatches()

  const [accountId, setAccountId] = useState('')
  const [fileName, setFileName] = useState('')
  const [buffer, setBuffer] = useState<ArrayBuffer | null>(null)
  const [mapping, setMapping] = useState<ImportMapping>(DEFAULT_MAPPING)
  const [step, setStep] = useState<'source' | 'map' | 'done'>('source')
  const [result, setResult] = useState<CommitImportResult | null>(null)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Default the account once they load.
  useEffect(() => {
    if (!accountId && accounts && accounts.length > 0) setAccountId(accounts[0].id)
  }, [accounts, accountId])

  // Load the account's remembered mapping, if any.
  useEffect(() => {
    if (!accountId) return
    let active = true
    getImportProfile(accountId).then((p) => {
      if (active && p) setMapping(profileToMapping(p))
    })
    return () => {
      active = false
    }
  }, [accountId])

  const catById = useMemo(() => {
    const m = new Map<string, Category>()
    for (const c of categories ?? []) m.set(c.id, c)
    return m
  }, [categories])
  const catLabel = (id: string | null): string => {
    if (!id) return '—'
    const c = catById.get(id)
    if (!c) return '—'
    return c.parentId ? `${catById.get(c.parentId)?.name ?? '?'} → ${c.name}` : c.name
  }

  const account = accounts?.find((a) => a.id === accountId)
  const currency = account?.currency ?? 'PLN'

  const rows = useMemo<string[][]>(() => {
    if (!buffer) return []
    return parseCsv(decodeBuffer(buffer, mapping.encoding), mapping.delimiter).rows
  }, [buffer, mapping.encoding, mapping.delimiter])

  const misdecoded = useMemo(
    () => (buffer ? looksMisdecoded(decodeBuffer(buffer, mapping.encoding)) : false),
    [buffer, mapping.encoding],
  )

  const numCols = useMemo(() => rows.reduce((m, r) => Math.max(m, r.length), 0), [rows])
  const columnLabels = useMemo(() => {
    const header = mapping.hasHeader ? rows[0] : undefined
    return Array.from({ length: numCols }, (_, i) => header?.[i]?.trim() || `Column ${i + 1}`)
  }, [rows, numCols, mapping.hasHeader])

  const parsed = useMemo(() => buildImportRows(rows, mapping), [rows, mapping])
  const validCount = parsed.filter((r) => r.valid).length
  const invalidCount = parsed.length - validCount

  async function onFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setError(null)
    setResult(null)
    const buf = await file.arrayBuffer()
    setBuffer(buf)
    setFileName(file.name)
  }

  const patch = (p: Partial<ImportMapping>) => setMapping((m) => ({ ...m, ...p }))

  async function doImport() {
    setImporting(true)
    setError(null)
    try {
      const res = await commitImport({ accountId, fileName, rows: parsed, mapping })
      setResult(res)
      setStep('done')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Import failed.')
    } finally {
      setImporting(false)
    }
  }

  function reset() {
    setBuffer(null)
    setFileName('')
    setResult(null)
    setError(null)
    setStep('source')
  }

  async function undo(batchId: string) {
    if (!window.confirm('Undo this import and delete its transactions?')) return
    await undoImportBatch(batchId)
    if (result?.batchId === batchId) reset()
  }

  const accountName = (id: string) => accounts?.find((a) => a.id === id)?.name ?? '—'

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        {step === 'source' && (
          <div className="space-y-4">
            <h2 className="text-sm font-semibold text-slate-700">Import CSV — choose file</h2>
            <div className="flex flex-wrap gap-4">
              <label className="flex flex-col gap-1">
                <span className="text-xs font-medium text-slate-600">Account</span>
                <select value={accountId} onChange={(e) => setAccountId(e.target.value)} className={inputCls}>
                  {accounts?.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name} ({a.currency})
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-xs font-medium text-slate-600">Encoding</span>
                <select
                  value={mapping.encoding}
                  onChange={(e) => patch({ encoding: e.target.value })}
                  className={inputCls}
                >
                  {SUPPORTED_ENCODINGS.map((enc) => (
                    <option key={enc} value={enc}>
                      {enc}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-xs font-medium text-slate-600">Delimiter</span>
                <select
                  value={mapping.delimiter}
                  onChange={(e) => patch({ delimiter: e.target.value })}
                  className={inputCls}
                >
                  {DELIMITERS.map((d) => (
                    <option key={d.label} value={d.value}>
                      {d.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <label className="flex flex-col gap-1">
              <span className="text-xs font-medium text-slate-600">CSV file</span>
              <input type="file" accept=".csv,text/csv,text/plain" onChange={onFile} className="text-sm" />
            </label>

            {misdecoded && (
              <p className="text-sm text-amber-600">
                Some characters look wrong — try the Windows-1250 encoding.
              </p>
            )}
            {buffer && rows.length > 0 && (
              <p className="text-xs text-slate-500">
                {fileName}: {rows.length} rows, {numCols} columns detected.
              </p>
            )}

            <button
              type="button"
              disabled={!accountId || !buffer || rows.length === 0}
              onClick={() => setStep('map')}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              Next: map columns
            </button>
          </div>
        )}

        {step === 'map' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-slate-700">Map columns</h2>
              <button type="button" onClick={() => setStep('source')} className="text-sm text-slate-500 hover:text-slate-800">
                ‹ Back
              </button>
            </div>

            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={mapping.hasHeader}
                onChange={(e) => patch({ hasHeader: e.target.checked })}
                className="h-4 w-4 rounded border-slate-300"
              />
              First row is a header
            </label>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Labeled text="Date column">
                <ColumnSelect value={mapping.dateIndex} columns={columnLabels} onChange={(i) => patch({ dateIndex: i })} />
              </Labeled>
              <Labeled text="Date format">
                <select value={mapping.dateFormat} onChange={(e) => patch({ dateFormat: e.target.value })} className={`${inputCls} w-full`}>
                  {DATE_FORMAT_OPTIONS.map((f) => (
                    <option key={f} value={f}>
                      {f}
                    </option>
                  ))}
                </select>
              </Labeled>
              <Labeled text="Description column">
                <ColumnSelect value={mapping.descriptionIndex} columns={columnLabels} onChange={(i) => patch({ descriptionIndex: i })} />
              </Labeled>
              <Labeled text="Amount columns">
                <div className="inline-flex rounded-lg border border-slate-200 p-0.5">
                  <ModeBtn active={mapping.amountMode === 'signed'} onClick={() => patch({ amountMode: 'signed' })}>
                    Signed
                  </ModeBtn>
                  <ModeBtn active={mapping.amountMode === 'debitCredit'} onClick={() => patch({ amountMode: 'debitCredit' })}>
                    Debit / Credit
                  </ModeBtn>
                </div>
              </Labeled>

              {mapping.amountMode === 'signed' ? (
                <>
                  <Labeled text="Amount column">
                    <ColumnSelect value={mapping.amountIndex} columns={columnLabels} onChange={(i) => patch({ amountIndex: i })} />
                  </Labeled>
                  <Labeled text="Sign convention">
                    <select
                      value={mapping.expenseIsNegative ? 'neg' : 'pos'}
                      onChange={(e) => patch({ expenseIsNegative: e.target.value === 'neg' })}
                      className={`${inputCls} w-full`}
                    >
                      <option value="neg">Negative = expense</option>
                      <option value="pos">Positive = expense</option>
                    </select>
                  </Labeled>
                </>
              ) : (
                <>
                  <Labeled text="Debit column (money out)">
                    <ColumnSelect value={mapping.debitIndex} columns={columnLabels} allowNone onChange={(i) => patch({ debitIndex: i })} />
                  </Labeled>
                  <Labeled text="Credit column (money in)">
                    <ColumnSelect value={mapping.creditIndex} columns={columnLabels} allowNone onChange={(i) => patch({ creditIndex: i })} />
                  </Labeled>
                </>
              )}
            </div>

            <div className="overflow-x-auto rounded-lg border border-slate-200">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-3 py-2 font-medium">Date</th>
                    <th className="px-3 py-2 font-medium">Description</th>
                    <th className="px-3 py-2 font-medium">Category</th>
                    <th className="px-3 py-2 text-right font-medium">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {parsed.slice(0, 8).map((r) => (
                    <tr key={r.index} className={r.valid ? '' : 'bg-red-50'}>
                      <td className="whitespace-nowrap px-3 py-2 text-slate-600">{r.date ?? '—'}</td>
                      <td className="px-3 py-2 text-slate-700">{r.description || '—'}</td>
                      <td className="px-3 py-2 text-slate-500">
                        {r.valid ? catLabel(matchCategory(r.description, rules ?? [])) : '—'}
                      </td>
                      <td className={`px-3 py-2 text-right ${r.type === 'expense' ? 'text-red-600' : 'text-emerald-600'}`}>
                        {r.valid && r.amountMinor != null ? (
                          <>
                            {r.type === 'expense' ? '−' : '+'}
                            {formatMinor(r.amountMinor, currency)}
                          </>
                        ) : (
                          <span className="text-red-500">{r.error}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                disabled={importing || validCount === 0}
                onClick={doImport}
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
              >
                {importing ? 'Importing…' : `Import ${validCount} transactions`}
              </button>
              <span className="text-xs text-slate-500">
                {validCount} valid{invalidCount > 0 ? `, ${invalidCount} invalid (skipped)` : ''}
              </span>
              {error && <span className="text-sm text-red-600">{error}</span>}
            </div>
          </div>
        )}

        {step === 'done' && result && (
          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-700">Import complete</h2>
            <p className="text-sm text-slate-700">
              Imported <span className="font-semibold">{result.imported}</span>, skipped{' '}
              {result.skippedDuplicates} duplicate{result.skippedDuplicates === 1 ? '' : 's'}
              {result.skippedInvalid > 0 ? ` and ${result.skippedInvalid} invalid` : ''}.
            </p>
            <div className="flex gap-3">
              <button type="button" onClick={reset} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800">
                Import another file
              </button>
              {result.imported > 0 && (
                <button
                  type="button"
                  onClick={() => undo(result.batchId)}
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-50"
                >
                  Undo this import
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {batches && batches.length > 0 && (
        <div className="rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
          <h2 className="px-2 py-2 text-sm font-semibold text-slate-700">Recent imports</h2>
          <ul className="divide-y divide-slate-100">
            {batches.map((b) => (
              <li key={b.id} className="flex items-center justify-between gap-3 px-2 py-2">
                <div className="min-w-0">
                  <div className="truncate text-sm text-slate-800">{b.fileName}</div>
                  <div className="text-xs text-slate-400">
                    {accountName(b.accountId)} · {b.count} transactions · {b.createdAt.slice(0, 10)}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => undo(b.id)}
                  className="shrink-0 rounded-md px-2 py-1 text-xs text-slate-400 hover:bg-red-50 hover:text-red-600"
                >
                  Undo
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function Labeled({ text, children }: { text: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-xs font-medium text-slate-600">{text}</span>
      {children}
    </label>
  )
}

function ColumnSelect({
  value,
  columns,
  onChange,
  allowNone = false,
}: {
  value: number
  columns: string[]
  onChange: (index: number) => void
  allowNone?: boolean
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(Number(e.target.value))}
      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500 focus:ring-1 focus:ring-slate-500"
    >
      {allowNone && <option value={-1}>—</option>}
      {columns.map((label, i) => (
        <option key={i} value={i}>
          {label}
        </option>
      ))}
    </select>
  )
}

function ModeBtn({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-md px-3 py-1.5 text-sm font-medium ${
        active ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'
      }`}
    >
      {children}
    </button>
  )
}
