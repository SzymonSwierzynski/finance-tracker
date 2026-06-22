import { db } from './db'
import { newId } from './ids'
import type { Category, CategoryKind } from './types'

export interface CreateCategoryInput {
  name: string
  kind: CategoryKind
  parentId?: string | null
  color?: string
}

const FALLBACK_COLOR = '#94a3b8' // slate-400

export async function createCategory(input: CreateCategoryInput): Promise<Category> {
  const category: Category = {
    id: newId(),
    name: input.name.trim(),
    kind: input.kind,
    parentId: input.parentId ?? null,
    color: input.color ?? FALLBACK_COLOR,
  }
  await db.categories.put(category)
  return category
}

export async function listCategories(kind?: CategoryKind): Promise<Category[]> {
  const all = await db.categories.toArray()
  const filtered = kind ? all.filter((c) => c.kind === kind) : all
  return filtered.sort((a, b) => a.name.localeCompare(b.name))
}

export async function getCategory(id: string): Promise<Category | undefined> {
  return db.categories.get(id)
}
