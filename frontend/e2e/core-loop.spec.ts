import { expect, test } from '@playwright/test'

/**
 * The core loop from CLAUDE.md §15, exercised against the real stack (SPA → Vite proxy → Spring
 * Boot → Postgres): register → add an account → add a transaction → see it. Each run registers a
 * fresh user so the shared dev database never collides. Form fields are scoped to the modal dialog
 * because the list pages carry same-named filter controls (e.g. an "Account" filter).
 */
function uniqueEmail(): string {
  return `e2e_${Date.now()}_${Math.floor(Math.random() * 1e6)}@example.com`
}

test('register, add an account, add an expense, and see it listed', async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel('Email').fill(uniqueEmail())
  await page.getByLabel('Password').fill('password123')
  await page.getByRole('button', { name: 'Sign up' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()

  // Create an account (defaults: checking / PLN).
  await page.getByRole('link', { name: 'Accounts' }).click()
  await page.getByRole('button', { name: 'New account' }).click()
  const accountForm = page.getByRole('dialog')
  await accountForm.getByLabel('Name').fill('E2E Checking')
  await accountForm.getByRole('button', { name: 'Save' }).click()
  // Exact match: a success toast ("E2E Checking ✓") also contains the name.
  await expect(page.getByText('E2E Checking', { exact: true })).toBeVisible()

  // Add an expense transaction against it.
  await page.getByRole('link', { name: 'Transactions' }).click()
  await page.getByRole('button', { name: 'New transaction' }).click()
  const txForm = page.getByRole('dialog')
  await txForm.getByLabel('Amount', { exact: true }).fill('42.50')
  await txForm.getByLabel('Account', { exact: true }).selectOption({ index: 1 }) // the one account
  await txForm.getByLabel(/^Description/).fill('E2E Coffee')
  await txForm.getByRole('button', { name: 'Save' }).click()

  // It round-trips back from the API into the list.
  await expect(page.getByText('E2E Coffee')).toBeVisible()
})
