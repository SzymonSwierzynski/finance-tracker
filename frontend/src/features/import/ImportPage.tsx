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
import { importApi } from './api'
import { useCommitImport, useImportBatches, usePreviewImport, useUndoImport } from './hooks'

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

export function ImportPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const { data: accounts } = useAccounts(false)
  const batches = useImportBatches()
  const preview = usePreviewImport()
  const commit = useCommitImport()
  const undo = useUndoImport()

  const [step, setStep] = useState<1 | 2 | 3>(1)
  const [accountId, setAccountId] = useState<number | ''>('')
  const [file, setFile] = useState<File | null>(null)
  const [mapping, setMapping] = useState<ImportMapping>(DEFAULT_MAPPING)
  const [previewData, setPreviewData] = useState<PreviewResponse | null>(null)

  const currency = accounts?.find((a) => a.id === accountId)?.currency ?? 'PLN'
  const importable = previewData ? previewData.validRows - previewData.duplicateRows : 0

  const reset = () => {
    setStep(1)
    setFile(null)
    setPreviewData(null)
    setMapping(DEFAULT_MAPPING)
  }

  const goToMapping = async () => {
    if (typeof accountId === 'number') {
      // Prefill the remembered mapping for this account, if any (404 -> keep defaults).
      try {
        setMapping(await importApi.getProfile(accountId))
      } catch {
        /* no saved profile */
      }
    }
    setStep(2)
  }

  const onError = (err: unknown) =>
    toast.error(err instanceof ApiError ? err.detail || err.title : t('errors.generic'))

  const runPreview = () => {
    if (typeof accountId !== 'number' || !file) return
    preview.mutate(
      { accountId, file, mapping },
      {
        onSuccess: (data) => {
          setPreviewData(data)
          setStep(3)
        },
        onError,
      },
    )
  }

  const runCommit = () => {
    if (typeof accountId !== 'number' || !file) return
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
    setMapping((m) => ({ ...m, [key]: value }))

  return (
    <div className="space-y-8">
      <PageHeader title={t('import.title')} subtitle={t('import.subtitle')} />

      <Card className="p-5">
        <ol className="mb-6 flex flex-wrap gap-2 text-xs font-medium">
          {([1, 2, 3] as const).map((s) => (
            <li
              key={s}
              className={`flex items-center gap-2 rounded-full px-3 py-1 ${step === s ? 'bg-brand-50 text-brand-700' : 'text-slate-400'}`}
            >
              <span
                className={`flex size-5 items-center justify-center rounded-full ${step >= s ? 'bg-brand-600 text-white' : 'bg-slate-200'}`}
              >
                {s}
              </span>
              {t(`import.step${s}`)}
            </li>
          ))}
        </ol>

        {step === 1 && (
          <div className="max-w-md space-y-4">
            <Field label={t('import.account')} htmlFor="imp-account">
              <Select
                id="imp-account"
                value={accountId}
                onChange={(e) => setAccountId(e.target.value ? Number(e.target.value) : '')}
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
                id="imp-file"
                type="file"
                accept=".csv,text/csv"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="block w-full text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-brand-700 hover:file:bg-brand-100"
              />
            </Field>
            <Button onClick={goToMapping} disabled={typeof accountId !== 'number' || !file}>
              {t('import.next')}
            </Button>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
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
                  onChange={(e) => set('descriptionIndex', Number(e.target.value))}
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
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setStep(1)}>
                {t('import.back')}
              </Button>
              <Button onClick={runPreview} loading={preview.isPending}>
                {t('import.preview')}
              </Button>
            </div>
          </div>
        )}

        {step === 3 && previewData && (
          <div className="space-y-4">
            {previewData.misdecoded && (
              <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
                {t('import.misdecoded')}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Badge tone="slate">{t('import.rows', { count: previewData.totalRows })}</Badge>
              <Badge tone="green">{t('import.valid', { count: importable })}</Badge>
              <Badge tone="slate">{t('import.duplicates', { count: previewData.duplicateRows })}</Badge>
              <Badge tone="red">
                {t('import.invalid', { count: previewData.totalRows - previewData.validRows })}
              </Badge>
            </div>
            <div className="max-h-96 overflow-auto rounded-lg border border-slate-200">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-slate-50 text-left text-xs text-slate-500">
                  <tr>
                    <th className="px-3 py-2 font-medium">{t('transactions.date')}</th>
                    <th className="px-3 py-2 font-medium">{t('transactions.description')}</th>
                    <th className="px-3 py-2 text-right font-medium">{t('transactions.amount')}</th>
                    <th className="px-3 py-2 font-medium">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {previewData.rows.map((row) => (
                    <tr
                      key={row.index}
                      className={!row.valid ? 'bg-red-50/60' : row.duplicate ? 'text-slate-400' : ''}
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
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setStep(2)}>
                {t('import.back')}
              </Button>
              <Button onClick={runCommit} loading={commit.isPending} disabled={importable === 0}>
                {t('import.commit')}
              </Button>
            </div>
          </div>
        )}
      </Card>

      <div>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">{t('import.batches')}</h2>
        {batches.isLoading ? (
          <Card className="p-5">
            <Skeleton className="h-5 w-48" />
          </Card>
        ) : !batches.data?.length ? (
          <Card className="p-5 text-sm text-slate-500">{t('import.noBatches')}</Card>
        ) : (
          <Card className="divide-y divide-slate-100 px-5">
            {batches.data.map((b) => (
              <div key={b.id} className="flex items-center justify-between py-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-800">{b.fileName}</p>
                  <p className="text-xs text-slate-500">
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
