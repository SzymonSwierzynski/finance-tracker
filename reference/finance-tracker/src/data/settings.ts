import { db } from './db'
import type { Settings } from './types'

export const SETTINGS_ID = 'app'
export const DEFAULT_REPORTING_CURRENCY = 'PLN'

function defaultSettings(): Settings {
  return { id: SETTINGS_ID, reportingCurrency: DEFAULT_REPORTING_CURRENCY, rates: {} }
}

/**
 * Read-only settings access — SAFE inside a Dexie liveQuery. Returns the stored
 * singleton, or an in-memory default WITHOUT writing. The row is actually
 * persisted by getSettings()/ensureSeeded() in a normal write context. (A
 * liveQuery querier runs read-only; writing from it throws ReadOnlyError.)
 */
export async function readSettings(): Promise<Settings> {
  return (await db.settings.get(SETTINGS_ID)) ?? defaultSettings()
}

/** Read-write: creates the default singleton on first access, then returns it. */
export async function getSettings(): Promise<Settings> {
  const existing = await db.settings.get(SETTINGS_ID)
  if (existing) return existing
  const fresh = defaultSettings()
  await db.settings.put(fresh)
  return fresh
}

export async function updateSettings(patch: Partial<Omit<Settings, 'id'>>): Promise<Settings> {
  const current = await getSettings()
  const next: Settings = { ...current, ...patch, id: SETTINGS_ID }
  await db.settings.put(next)
  return next
}

/** Store/overwrite the locked rate for a currency (relative to base). */
export async function setRate(currency: string, rateToBase: number): Promise<Settings> {
  const current = await getSettings()
  return updateSettings({ rates: { ...current.rates, [currency]: rateToBase } })
}

/**
 * Rate converting `currency` minor units into base (reporting) minor units.
 * Reporting currency is always 1; unknown currencies return null so the caller
 * knows it must supply a rate explicitly. Pure (no DB access).
 */
export function resolveRateToBase(settings: Settings, currency: string): number | null {
  if (currency === settings.reportingCurrency) return 1
  const raw = settings.rates[currency]
  return Number.isFinite(raw) ? raw : null
}

export async function getRateToBase(currency: string): Promise<number | null> {
  return resolveRateToBase(await getSettings(), currency)
}
