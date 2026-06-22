/**
 * Small deterministic hash used to dedupe transactions (relied on by CSV import
 * in a later phase). Manual entries compute one too, so the field is always
 * populated and consistent. FNV-1a, 32-bit, hex-encoded.
 */
export function dedupeHash(parts: Array<string | number>): string {
  const input = parts.map((p) => String(p)).join('|')
  let h = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return (h >>> 0).toString(16).padStart(8, '0')
}
