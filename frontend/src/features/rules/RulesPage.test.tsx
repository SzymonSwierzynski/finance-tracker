import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApplyRulesResult, Category, Rule } from '@/api'
import { renderWithProviders } from '@/test/test-utils'
import { RulesPage } from './RulesPage'
import { rulesApi } from './api'
import { categoriesApi } from '@/features/categories/api'

vi.mock('./api', () => ({
  rulesApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), apply: vi.fn() },
}))
vi.mock('@/features/categories/api', () => ({
  categoriesApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))

const categories: Category[] = [
  { id: 10, name: 'Groceries', kind: 'expense', parentId: null, color: '#22c55e', version: 0 },
]

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(categoriesApi.list).mockResolvedValue(categories)
  vi.mocked(rulesApi.list).mockResolvedValue([])
})

describe('<RulesPage />', () => {
  it('shows the empty state when there are no rules', async () => {
    renderWithProviders(<RulesPage />)
    expect(await screen.findByText(/no rules yet/i)).toBeInTheDocument()
  })

  it('lists a rule with its resolved category name and priority', async () => {
    const rules: Rule[] = [{ id: 1, pattern: 'biedronka', categoryId: 10, priority: 5, version: 0 }]
    vi.mocked(rulesApi.list).mockResolvedValue(rules)
    renderWithProviders(<RulesPage />)
    expect(await screen.findByText(/biedronka/)).toBeInTheDocument()
    expect(screen.getByText(/Groceries/)).toBeInTheDocument()
  })

  it('applies rules and toasts how many were categorized', async () => {
    const user = userEvent.setup()
    const rules: Rule[] = [{ id: 1, pattern: 'biedronka', categoryId: 10, priority: 0, version: 0 }]
    const result: ApplyRulesResult = { scanned: 3, categorized: 2 }
    vi.mocked(rulesApi.list).mockResolvedValue(rules)
    vi.mocked(rulesApi.apply).mockResolvedValue(result)

    renderWithProviders(<RulesPage />)
    await screen.findByText(/biedronka/)
    await user.click(screen.getByRole('button', { name: /apply to uncategorized/i }))

    expect(await screen.findByText(/2 of 3 transaction/i)).toBeInTheDocument()
    expect(rulesApi.apply).toHaveBeenCalledOnce()
  })
})
