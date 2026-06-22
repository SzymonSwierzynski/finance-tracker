/** A small starter set for the currency pickers. Users can type any code. */
export const COMMON_CURRENCIES = ['PLN', 'EUR', 'USD', 'GBP', 'CHF', 'CZK', 'UAH'] as const

/** De-duplicated currency list with the given codes floated to the front. */
export function currencyOptions(...preferred: Array<string | undefined>): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const c of [...preferred, ...COMMON_CURRENCIES]) {
    if (c && !seen.has(c)) {
      seen.add(c)
      out.push(c)
    }
  }
  return out
}
