import { db } from './db'
import { newId } from './ids'
import type { Rule } from './types'

export interface CreateRuleInput {
  pattern: string
  categoryId: string
  priority?: number
}

export async function createRule(input: CreateRuleInput): Promise<Rule> {
  const rule: Rule = {
    id: newId(),
    pattern: input.pattern.trim(),
    categoryId: input.categoryId,
    priority: input.priority ?? 0,
  }
  await db.rules.put(rule)
  return rule
}

/** Highest priority first; ties broken alphabetically by pattern. */
export async function listRules(): Promise<Rule[]> {
  const all = await db.rules.toArray()
  return all.sort((a, b) => b.priority - a.priority || a.pattern.localeCompare(b.pattern))
}

export async function updateRule(id: string, patch: Partial<Omit<Rule, 'id'>>): Promise<void> {
  await db.rules.update(id, patch)
}

export async function deleteRule(id: string): Promise<void> {
  await db.rules.delete(id)
}
