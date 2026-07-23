import { expect, test } from '@playwright/test'

/**
 * Phase 7 budgets, end to end: create a category, spend against it, set a budget, and confirm the
 * page reflects the spend (limit 100 − spend 30 = 70 remaining) — proving the whole progress
 * pipeline (transaction → base-currency roll-up → budget) works through the real stack. Form fields
 * are scoped to the modal dialog to avoid clashing with same-named list-page filters.
 */
function uniqueEmail(): string {
  return `e2e_${Date.now()}_${Math.floor(Math.random() * 1e6)}@example.com`
}

test('set a budget and see this month’s spend counted against it', async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel('Email').fill(uniqueEmail())
  await page.getByLabel('Password').fill('password123')
  await page.getByRole('button', { name: 'Sign up' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()

  // An expense category to budget.
  await page.getByRole('link', { name: 'Categories' }).click()
  await page.getByRole('button', { name: 'New category' }).click()
  const catForm = page.getByRole('dialog')
  await catForm.getByLabel('Name').fill('E2E Groceries')
  await catForm.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('E2E Groceries')).toBeVisible()

  // An account and a 30.00 expense in that category (dated today → the current budget month).
  await page.getByRole('link', { name: 'Accounts' }).click()
  await page.getByRole('button', { name: 'New account' }).click()
  const accForm = page.getByRole('dialog')
  await accForm.getByLabel('Name').fill('E2E Bank')
  await accForm.getByRole('button', { name: 'Save' }).click()
  await expect(page.getByText('E2E Bank', { exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Transactions' }).click()
  await page.getByRole('button', { name: 'New transaction' }).click()
  const txForm = page.getByRole('dialog')
  await txForm.getByLabel('Amount', { exact: true }).fill('30.00')
  await txForm.getByLabel('Account', { exact: true }).selectOption({ index: 1 }) // the one account
  await txForm.getByLabel(/^Category/).selectOption({ label: 'E2E Groceries' })
  await txForm.getByRole('button', { name: 'Save' }).click()

  // A 100.00 budget on that category; the page shows 70.00 remaining (100 − 30).
  await page.getByRole('link', { name: 'Budgets' }).click()
  await page.getByRole('button', { name: 'New budget' }).click()
  const budgetForm = page.getByRole('dialog')
  await budgetForm.getByLabel('Category', { exact: true }).selectOption({ label: 'E2E Groceries' })
  await budgetForm.getByLabel(/Monthly limit/).fill('100.00')
  await budgetForm.getByRole('button', { name: 'Save' }).click()

  await expect(page.getByText('E2E Groceries')).toBeVisible()
  await expect(page.getByText(/70[.,]00/)).toBeVisible()
})
