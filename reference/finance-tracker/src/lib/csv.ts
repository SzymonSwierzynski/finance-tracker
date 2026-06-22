import Papa from 'papaparse'

/** Encodings offered in the import UI (Polish exports are often Windows-1250). */
export const SUPPORTED_ENCODINGS = ['utf-8', 'windows-1250'] as const

/** Decode raw file bytes with the chosen encoding (non-fatal). */
export function decodeBuffer(buf: ArrayBuffer, encoding: string): string {
  return new TextDecoder(encoding || 'utf-8', { fatal: false }).decode(buf)
}

/** U+FFFD replacement chars are a strong hint the encoding is wrong. */
export function looksMisdecoded(text: string): boolean {
  return text.includes('�')
}

export interface ParsedCsv {
  rows: string[][]
  /** The delimiter PapaParse used (useful when auto-detecting). */
  delimiter: string
}

/**
 * Parse CSV text into rows of string cells. An empty `delimiter` lets
 * PapaParse auto-detect (handles the ';' that Polish exports favour).
 */
export function parseCsv(text: string, delimiter: string): ParsedCsv {
  const result = Papa.parse<string[]>(text, {
    delimiter: delimiter || undefined,
    skipEmptyLines: 'greedy',
  })
  return { rows: result.data, delimiter: result.meta.delimiter || delimiter }
}
