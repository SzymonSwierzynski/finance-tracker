import { db } from './db'
import { newId } from './ids'
import type { Account, AccountType } from './types'

export interface CreateAccountInput {
  name: string
  type: AccountType
  currency: string
  trackBalance?: boolean
  startingBalanceMinor?: number | null
}

export async function createAccount(input: CreateAccountInput): Promise<Account> {
  const trackBalance = input.trackBalance ?? false
  const account: Account = {
    id: newId(),
    name: input.name.trim(),
    type: input.type,
    currency: input.currency,
    trackBalance,
    // Starting balance is only meaningful when we're tracking balance.
    startingBalanceMinor: trackBalance ? input.startingBalanceMinor ?? null : null,
    archived: false,
  }
  await db.accounts.put(account)
  return account
}

export async function listAccounts(includeArchived = false): Promise<Account[]> {
  const all = await db.accounts.orderBy('name').toArray()
  return includeArchived ? all : all.filter((a) => !a.archived)
}

export async function getAccount(id: string): Promise<Account | undefined> {
  return db.accounts.get(id)
}

export async function updateAccount(id: string, patch: Partial<Omit<Account, 'id'>>): Promise<void> {
  await db.accounts.update(id, patch)
}

export async function archiveAccount(id: string, archived = true): Promise<void> {
  await db.accounts.update(id, { archived })
}
