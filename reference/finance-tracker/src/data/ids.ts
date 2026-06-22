/** App-generated primary keys. UUIDs keep rows portable to a future backend. */
export function newId(): string {
  return crypto.randomUUID()
}
