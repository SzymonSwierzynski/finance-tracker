/**
 * Auto-categorization matcher. Structural input (any object with these fields,
 * e.g. a Rule) so this stays free of the data layer.
 */
export interface MatchableRule {
  pattern: string
  categoryId: string
  priority: number
}

/**
 * The category for a description, or null. Rules are tried highest-priority
 * first; a rule matches when its (non-empty) pattern is a case-insensitive
 * substring of the description.
 */
export function matchCategory(description: string, rules: MatchableRule[]): string | null {
  const haystack = description.toLowerCase()
  const sorted = [...rules].sort((a, b) => b.priority - a.priority)
  for (const rule of sorted) {
    const needle = rule.pattern.trim().toLowerCase()
    if (needle && haystack.includes(needle)) return rule.categoryId
  }
  return null
}
