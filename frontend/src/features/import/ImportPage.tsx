import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ImportMapping, PreviewResponse } from '@/api'
import { AMOUNT_MODES, ApiError, DATE_FORMAT_OPTIONS, SUPPORTED_ENCODINGS } from '@/api'
import {
  Badge,
  Button,
  Card,
  Field,
  Input,
  PageHeader,
  Select,
  Skeleton,
} from '@/components/primitives'
import { Money } from '@/components/Money'
import { useToast } from '@/components/Toast'
import { useAccounts } from '@/features/accounts/hooks'
import { useCommitImport, useImportBatches, usePreviewImport, useUndoImport } from './hooks'

export function ImportPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const { data: accounts } = useAccounts(false)
  const batches = useImportBatches()
  const preview = usePreviewImport()
  const commit = useCommitImport()
  const undo = useUndoImport()

  const [accountId, setAccountId] = useState<number | ''>('')
  const [file, setFile] = useState<File | null>(null)
  const [fileKey, setFileKey] = useState(0) // bump to clear the uncontrolled file input after commit
  // The mapping actually in use — seeded from the server's detection, then editable via "Adjust columns".
  const [mapping, setMapping] = useState<ImportMapping | null>(null)
  const [previewData, setPreviewData] = useState<PreviewResponse | null>(null)
  const [adjusting, setAdjusting] = useState(false)

  const currency = accounts?.find((a) => a.id === accountId)?.currency ?? 'PLN'
  const importable = previewData ? previewData.validRows - previewData.duplicateRows : 0

  const onError = (err: unknown) =>
    toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))

  /** Run a preview. `withMapping` null → the backend auto-detects (or uses a saved profile). */
  const runPreview = (acc: number, f: File, withMapping: ImportMapping | null) => {
    preview.mutate(
      { accountId: acc, file: f, mapping: withMapping },
      {
        onSuccess: (data) => {
          setPreviewData(data)
          setMapping(data.mapping) // seed "Adjust columns" from what was actually used
        },
        onError,
      },
    )
  }

  // Detect-first: as soon as an account and a file are both chosen, auto-detect and preview.
  const detect = (acc: number | '', f: File | null) => {
    setAdjusting(false)
    if (typeof acc === 'number' && f) {
      runPreview(acc, f, null)
    } else {
      setPreviewData(null)
      setMapping(null)
    }
  }

  const reset = () => {
    setFile(null)
    setFileKey((k) => k + 1)
    setPreviewData(null)
    setMapping(null)
    setAdjusting(false)
  }

  const runCommit = () => {
    if (typeof accountId !== 'number' || !file || !mapping) return
    commit.mutate(
      { accountId, file, mapping },
      {
        onSuccess: (res) => {
          toast.success(
            t('import.imported', {
              imported: res.imported,
              duplicates: res.skippedDuplicates,
              invalid: res.skippedInvalid,
            }),
          )
          reset()
        },
        onError,
      },
    )
  }

  const onUndo = (id: number, count: number) => {
    if (!window.confirm(t('import.undoConfirm', { count }))) return
    undo.mutate(id, {
      onSuccess: () => toast.success(t('import.undone', { count })),
      onError: () => toast.error(t('errors.generic')),
    })
  }

  const set = <K extends keyof ImportMapping>(key: K, value: ImportMapping[K]) =>
    setMapping((m) => (m ? { ...m, [key]: value } : m))

  return (
    <div className="space-y-8">
      <PageHeader title={t('import.title')} subtitle={t('import.subtitle')} />

      <Card className="space-y-6 p-5">
        <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
          <Field label={t('import.account')} htmlFor="imp-account">
            <Select
              id="imp-account"
              value={accountId}
              onChange={(e) => {
                const v = e.target.value ? Number(e.target.value) : ''
                setAccountId(v)
                detect(v, file)
              }}
            >
              <option value="">—</option>
              {(accounts ?? []).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} ({a.currency})
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t('import.file')} htmlFor="imp-file">
            <input
              key={fileKey}
              id="imp-file"
              type="file"
              accept=".csv,text/csv"
              onChange={(e) => {
                const f = e.target.files?.[0] ?? null
                setFile(f)
                detect(accountId, f)
              }}
              className="block w-full text-sm text-fg-muted file:mr-3 file:rounded-lg file:border-0 file:bg-accent-soft file:px-3 file:py-2 file:text-sm file:font-medium file:text-accent hover:file:bg-accent-soft"
            />
          </Field>
        </div>

        {preview.isPending && !previewData && <Skeleton className="h-24 w-full" />}

        {previewData && (
          <div className="space-y-4">
            {/* Detection banner (auto-detected only) + income/expense sanity totals. */}
            <div className="rounded-lg border border-border bg-surface-2 px-4 py-2.5 text-sm">
              {previewData.detection && (
                <div>
                  <span className="font-medium">{t('import.detected')}:</span>{' '}
                  {previewData.detection.encoding} ·{' '}
                  {t('import.headerRow', {
                    row: (previewData.detection.headerRowIndex ?? 0) + 1,
                  })}
                  {Object.entries(previewData.detection.recognizedColumns).map(([role, label]) => (
                    <span key={role} className="ml-2 text-fg-soft">
                      · {t(`import.role_${role}`)}: {label}
                    </span>
                  ))}
                </div>
              )}
              <div className={previewData.detection ? 'mt-1 text-fg-soft' : 'text-fg-soft'}>
                {t('import.income')}:{' '}
                <Money minor={previewData.incomeMinor} currency={currency} /> ·{' '}
                {t('import.expense')}:{' '}
                <Money minor={previewData.expenseMinor} currency={currency} />
              </div>
            </div>

            {previewData.misdecoded && (
              <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
                {t('import.misdecoded')}
              </p>
            )}

            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="slate">{t('import.rows', { count: previewData.totalRows })}</Badge>
              <Badge tone="green">{t('import.valid', { count: importable })}</Badge>
              <Badge tone="slate">{t('import.duplicates', { count: previewData.duplicateRows })}</Badge>
              <Badge tone="red">
                {t('import.invalid', { count: previewData.totalRows - previewData.validRows })}
              </Badge>
              <Button
                variant="ghost"
                size="sm"
                className="ml-auto"
                onClick={() => setAdjusting((v) => !v)}
              >
                {t('import.adjustColumns')}
              </Button>
            </div>

            {adjusting && mapping && (
              <div className="space-y-4 rounded-lg border border-border-subtle p-4">
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  <Field label={t('import.encoding')} htmlFor="imp-enc">
                    <Select id="imp-enc" value={mapping.encoding} onChange={(e) => set('encoding', e.target.value)}>
                      {SUPPORTED_ENCODINGS.map((enc) => (
                        <option key={enc} value={enc}>
                          {enc}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  <Field label={t('import.delimiter')} htmlFor="imp-delim">
                    <Select id="imp-delim" value={mapping.delimiter} onChange={(e) => set('delimiter', e.target.value)}>
                      <option value="">{t('import.autoDetect')}</option>
                      <option value=";">;</option>
                      <option value=",">,</option>
                      <option value={'\t'}>Tab</option>
                    </Select>
                  </Field>
                  <Field label={t('import.hasHeader')} htmlFor="imp-header">
                    <Select
                      id="imp-header"
                      value={mapping.hasHeader ? '1' : '0'}
                      onChange={(e) => set('hasHeader', e.target.value === '1')}
                    >
                      <option value="1">{t('common.yes')}</option>
                      <option value="0">{t('common.no')}</option>
                    </Select>
                  </Field>
                  <Field label={t('import.dateColumn')} htmlFor="imp-datecol">
                    <Input
                      id="imp-datecol"
                      type="number"
                      min={0}
                      value={mapping.dateIndex}
                      onChange={(e) => set('dateIndex', Number(e.target.value))}
                    />
                  </Field>
                  <Field label={t('import.dateFormat')} htmlFor="imp-datefmt">
                    <Select id="imp-datefmt" value={mapping.dateFormat} onChange={(e) => set('dateFormat', e.target.value)}>
                      {DATE_FORMAT_OPTIONS.map((f) => (
                        <option key={f} value={f}>
                          {f === 'auto' ? t('import.autoDetect') : f}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  <Field label={t('import.descriptionColumn')} htmlFor="imp-desccol">
                    <Input
                      id="imp-desccol"
                      type="number"
                      min={0}
                      value={mapping.descriptionIndex}
                      onChange={(e) =>
                        // Editing the scalar column clears any multi-column detection so it takes effect.
                        setMapping((m) =>
                          m ? { ...m, descriptionIndex: Number(e.target.value), descriptionIndexes: null } : m,
                        )
                      }
                    />
                  </Field>
                  <Field label={t('import.amountMode')} htmlFor="imp-mode">
                    <Select
                      id="imp-mode"
                      value={mapping.amountMode}
                      onChange={(e) => set('amountMode', e.target.value as ImportMapping['amountMode'])}
                    >
                      {AMOUNT_MODES.map((m) => (
                        <option key={m} value={m}>
                          {t(`import.${m}`)}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  {mapping.amountMode === 'signed' ? (
                    <>
                      <Field label={t('import.amountColumn')} htmlFor="imp-amt">
                        <Input
                          id="imp-amt"
                          type="number"
                          min={0}
                          value={mapping.amountIndex}
                          onChange={(e) => set('amountIndex', Number(e.target.value))}
                        />
                      </Field>
                      <Field label={t('import.expenseIsNegative')} htmlFor="imp-neg">
                        <Select
                          id="imp-neg"
                          value={mapping.expenseIsNegative ? '1' : '0'}
                          onChange={(e) => set('expenseIsNegative', e.target.value === '1')}
                        >
                          <option value="1">{t('common.yes')}</option>
                          <option value="0">{t('common.no')}</option>
                        </Select>
                      </Field>
                    </>
                  ) : (
                    <>
                      <Field label={t('import.debitColumn')} htmlFor="imp-debit">
                        <Input
                          id="imp-debit"
                          type="number"
                          min={0}
                          value={mapping.debitIndex}
                          onChange={(e) => set('debitIndex', Number(e.target.value))}
                        />
                      </Field>
                      <Field label={t('import.creditColumn')} htmlFor="imp-credit">
                        <Input
                          id="imp-credit"
                          type="number"
                          min={0}
                          value={mapping.creditIndex}
                          onChange={(e) => set('creditIndex', Number(e.target.value))}
                        />
                      </Field>
                    </>
                  )}
                </div>
                <Button
                  onClick={() => {
                    if (typeof accountId === 'number' && file) runPreview(accountId, file, mapping)
                  }}
                  loading={preview.isPending}
                >
                  {t('import.preview')}
                </Button>
              </div>
            )}

            <div className="max-h-96 overflow-auto rounded-lg border border-border">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-surface-2 text-left text-xs text-fg-soft">
                  <tr>
                    <th className="px-3 py-2 font-medium">{t('transactions.date')}</th>
                    <th className="px-3 py-2 font-medium">{t('transactions.description')}</th>
                    <th className="px-3 py-2 text-right font-medium">{t('transactions.amount')}</th>
                    <th className="px-3 py-2 font-medium">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-subtle">
                  {previewData.rows.map((row) => (
                    <tr
                      key={row.index}
                      className={!row.valid ? 'bg-red-50/60' : row.duplicate ? 'text-fg-subtle' : ''}
                    >
                      <td className="whitespace-nowrap px-3 py-1.5">{row.date ?? '—'}</td>
                      <td className="max-w-xs truncate px-3 py-1.5">{row.description}</td>
                      <td className="px-3 py-1.5 text-right">
                        {row.amountMinor != null ? (
                          <Money
                            minor={row.type === 'expense' ? -row.amountMinor : row.amountMinor}
                            currency={currency}
                            colored
                          />
                        ) : (
                          '—'
                        )}
                      </td>
                      <td className="px-3 py-1.5">
                        {!row.valid ? (
                          <Badge tone="red">{t('import.invalidRow')}</Badge>
                        ) : row.duplicate ? (
                          <Badge tone="slate">{t('import.duplicate')}</Badge>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Button onClick={runCommit} loading={commit.isPending} disabled={importable === 0}>
              {t('import.commit')}
            </Button>
          </div>
        )}
      </Card>

      <div>
        <h2 className="mb-3 text-lg font-semibold text-fg">{t('import.batches')}</h2>
        {batches.isLoading ? (
          <Card className="p-5">
            <Skeleton className="h-5 w-48" />
          </Card>
        ) : !batches.data?.length ? (
          <Card className="p-5 text-sm text-fg-soft">{t('import.noBatches')}</Card>
        ) : (
          <Card className="divide-y divide-border-subtle px-5">
            {batches.data.map((b) => (
              <div key={b.id} className="flex items-center justify-between py-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-fg">{b.fileName}</p>
                  <p className="text-xs text-fg-soft">
                    {t('import.rows', { count: b.count })} · {new Date(b.createdAt).toLocaleString()}
                  </p>
                </div>
                <Button variant="ghost" size="sm" onClick={() => onUndo(b.id, b.count)}>
                  {t('import.undo')}
                </Button>
              </div>
            ))}
          </Card>
        )}
      </div>
    </div>
  )
}
