# Smart CSV import — Design Spec

**Date:** 2026-09-02
**Branch:** `backlog-smart-csv-import` (off `main`)
**Status:** Approved design, ready for implementation planning
**Related:** `CLAUDE.md` §8 (import rules), §10 (Smart CSV — was deferred, now prioritized); `HANDOFF.md` §22 (original sketch — superseded by this spec). Builds on the shipped Phase-4 importer.

---

## 1. Goal

Make CSV import **detect-first**: *pick account → drop file → confirm → done*, with the file never
edited. Today the engine already handles arbitrary layouts, `;`/`,`/tab, UTF-8/Windows-1250/ISO-8859-2,
decimal-comma amounts, signed/debit-credit modes, per-account remembered profiles, dedupe and undoable
batches — but the user must **hand-build the column mapping on the first import**, and files with a
**metadata preamble/summary** (mBank export: header on a mid-file row) cannot be imported at all,
because `ImportRowBuilder` assumes `hasHeader` skips exactly one leading line.

Smart import closes exactly that gap by **auto-detecting**: encoding, the header row (skipping
preamble/summary/footer), and the full column mapping (date / amount / description / mode / sign /
format) via a **language-driven header dictionary** — *not* per-bank presets. mBank is the proven
reference case; the detector generalizes to any bank whose header names are in the dictionary.

---

## 2. Decisions (locked)

- **Test oracle = a synthesized fixture** committed to the repo (`§7`), with computed acceptance
  numbers. The real mBank export can be dropped in later as a second fixture without changing code.
- **Generic header-name dictionary**, PL + EN, with mBank as the reference — no per-bank branches.
- **Detection is opt-in-by-absence:** `preview`/`commit` accept an **optional** mapping. Absent →
  the service detects and returns the mapping it used. Present → used as given (the manual / "adjust
  columns" path), fully back-compatible with today's behavior.
- **Preamble handled by header detection; footer/summary handled by row validity.** Data starts at
  `headerRowIndex + 1`; trailing summary rows have no parseable date and are already dropped as
  invalid (existing `ImportRowBuilder` + commit skip-invalid). No separate "data end" needed.
- **Next migration is `V16`** (`CLAUDE.md` §5 — the §22 draft's `V13` name is stale).
- **Parsed-row indices, not file-line numbers.** `CsvParser` sets `ignoreEmptyLines(true)`, so all
  detection works on the *parsed* row list (blank lines already removed).

---

## 3. Detection algorithm

Run as a pipeline in a new `importing/detect/` sub-package. Each stage is pure and unit-tested.

### 3.1 Encoding — `EncodingDetector.detect(byte[]) → String`
Decode as UTF-8; if `CsvDecoder.looksMisdecoded` (contains U+FFFD) → return `"windows-1250"` (the
Polish-bank default; ISO-8859-2 remains a manual fallback in the UI). Else `"utf-8"`. Deterministic
and testable: a CP1250 file has single-byte `ł/ż/ó` that are invalid UTF-8 multibyte → dirty decode →
picks `windows-1250`.

### 3.2 Delimiter — improve `CsvParser.detectDelimiter`
Today it sniffs only the **first non-empty line**; with a preamble that can be a title line. Change it
to score each candidate (`;`, `,`, `\t`) across the **first 40 non-empty lines** by the count of lines
whose occurrence-count equals the modal count (consistency), tie-broken by higher modal count. Robust
for mBank (`;`), CSV-comma and TSV. Falls back to `,`.

### 3.3 Header row — `HeaderDetector.detect(List<List<String>> rows) → int` (`-1` if none)
Scan the first 50 parsed rows. **Normalize** each cell: strip a leading `#`, surrounding quotes,
lowercase (Polish-aware, `Locale.forLanguageTag("pl")`), collapse whitespace, trim. Score a row by the
number of cells that match the **header dictionary** (§3.5). The row with the highest score wins,
provided score ≥ **3** and it is not itself all-numeric/date (a data row). Return its 0-based parsed
index; `-1` when nothing scores ≥ 3 (caller falls back to `hasHeader ? 0 : -1`).

### 3.4 Column roles — `MappingDetector.detect(rows, headerRowIndex) → DetectedMapping`
From the header cells (normalized) + a sample of up to 20 data rows below the header:
- **Date:** header cells matching *date* vocab. Prefer `operacji` > `transakcji` > `waluty` >
  `księgowania` > generic `data`/`date`. Confirm by value-sniffing sample cells with
  `CsvDateParser.parseFlexible`; pick the `dateFormat` from `AUTO_DATE_FORMATS` that parses the most
  samples. Result: `dateIndex`, `dateFormat`.
- **Amount:** header cells matching *amount* vocab **and not** *balance* vocab (**Saldo guard**). If a
  single signed column (`kwota`) → `AmountMode.SIGNED`, `amountIndex` = it, `expenseIsNegative` = true
  when the sample contains any negative value (Polish exports use negative for outflow; default true).
  If separate `obciążenia`/`uznania` columns → `AmountMode.DEBIT_CREDIT`, `debitIndex`/`creditIndex`.
- **Description:** header cells matching *description* vocab, in header order → `descriptionIndexes`
  (e.g. `[2,3]` = Opis + Tytuł). **Counterparty columns** (`nadawca/odbiorca`, `nadawca`, `odbiorca`,
  `kontrahent`) are kept in the dictionary so the header row still *scores* high in detection, but are
  **excluded from the emitted description** (a `COUNTERPARTY` policy list in `MappingDetector`) — they
  are sender/recipient names, not transaction narrative, and are usually redundant with Opis/Tytuł.
  *Known limitation:* a bank whose only text column is the counterparty would get an empty description
  (rare; a follow-up could fall back to it).
- **Guards:** cells matching *balance* or *account* vocab are never chosen for date/amount/description.
- **Confidence** = resolved-core-roles / 3 (date, amount, description). `recognizedColumns` maps each
  resolved role to its raw header label for the UI banner.

### 3.5 Header dictionary (normalized tokens)
| role | tokens (PL + EN) |
|---|---|
| date | `data operacji`, `data transakcji`, `data księgowania`, `data waluty`, `data`, `date` |
| amount (signed) | `kwota`, `kwota operacji`, `amount` |
| amount (debit) | `obciążenia`, `kwota obciążenia`, `wypłata`, `debit` |
| amount (credit) | `uznania`, `kwota uznania`, `wpłata`, `credit` |
| description | `opis`, `opis operacji`, `tytuł`, `tytuł operacji`, `nadawca`, `odbiorca`, `nadawca/odbiorca`, `kontrahent`, `szczegóły`, `description`, `details`, `title` |
| balance (guard) | `saldo`, `saldo po operacji`, `balance` |
| account (guard) | `numer rachunku`, `rachunek`, `numer konta`, `konto`, `account` |

Matching is exact-after-normalize with a substring fallback for the multiword date/amount/balance
tokens. Dictionary lives in one class constant — the single tuning surface for new banks.

---

## 4. DTO / entity / migration changes

- **`ImportMapping`** (record): add `Integer headerRowIndex` (nullable; `null` = auto, else the parsed
  row index of the header) and `List<Integer> descriptionIndexes` (nullable; when non-empty, overrides
  the scalar `descriptionIndex`). Keep `descriptionIndex` for back-compat. All new fields optional.
- **`PreviewResponse`**: add `ImportMapping mapping` (the mapping actually used — detected or supplied),
  `DetectionInfo detection` (nullable; `encoding`, `headerRowIndex`, `Map<String,String> recognizedColumns`,
  `bankHint`), and `long incomeMinor` / `long expenseMinor` (summed over valid rows, so a wrong
  sign/column is visible before commit).
- **`ImportProfile`** entity + **`V16__import_profile_detection.sql`**: add `header_row_index int`
  (nullable) and `description_indexes text` (nullable, comma-joined). Forward-only; existing profiles
  keep working (null → single `descriptionIndex`, header via `hasHeader`).
- **`ImportRowBuilder.build`**: start at `headerRowIndex != null ? headerRowIndex + 1 : (hasHeader ? 1 : 0)`;
  build description by joining the `descriptionIndexes` cells with a space (falling back to the scalar
  index). Existing single-index callers unaffected.

---

## 5. API changes

- `POST /imports/preview` and `/commit`: the `mapping` multipart part becomes **optional**
  (`@RequestPart(required = false)`). Absent → `ImportService` runs detection (seeded by the account's
  saved `ImportProfile` if one exists) and returns the used mapping + `DetectionInfo` + income/expense
  sums in `PreviewResponse`. Present → today's behavior.
- No new endpoints. `GET/PUT /imports/profiles/{accountId}` and the batch endpoints are unchanged
  (profile now round-trips the two new fields).

---

## 6. Frontend (detect-first) — `features/import/`

- Flip `ImportPage` from a 3-step manual wizard to **detect-first**: on *account + file* chosen, call
  `preview` **with no mapping**; render the mapped preview immediately (skip the manual step 2 default).
- **Detection banner** (plain language): "Detected: Windows-1250 · `;` · header row 6 · date from
  *Data operacji* · amount from *Kwota* · 10 rows" + the **income/expense sanity totals**.
- **"Adjust columns" expander** (fallback only): the current column controls, seeded from the detected
  mapping; changing them re-previews with an explicit mapping. Commit sends the detected-or-adjusted
  mapping.
- Regenerate `types.gen.ts` after the DTO change; keep curated aliases in `types.ts`. PL + EN strings
  for the banner/labels.

---

## 7. Synthesized fixture + computed oracle

`backend/src/test/resources/imports/mbank_sample.csv`, **Windows-1250**, `;`-delimited. Structure:
5 preamble rows (`#Klient`, `#Za okres`, `#Waluta`, `#Numer rachunku`, a title), a blank line, the
header row, **10 data rows**, a blank line, a 4-row summary footer. Columns (8):

`0 #Data księgowania · 1 #Data operacji · 2 #Opis operacji · 3 #Tytuł · 4 #Nadawca/Odbiorca · 5 #Numer rachunku · 6 #Kwota · 7 #Saldo po operacji`

Because `CsvParser` drops blank lines, in **parsed** rows the header is index **5** and data is rows
6–15. The generator (a plan step) writes UTF-8 then `iconv`s to CP1250. **Amounts** use space-thousands
+ comma-decimals to exercise `MoneyUtil`. **Descriptions** contain `ł/ż/ó/ą` to exercise encoding.

**Data rows (Kwota):** −45,99 · −129,00 · −12,50 · −210,30 · −43,00 · −8,90 · −1 500,00 (7 expense);
5 000,00 · 25,00 · 150,00 (3 income). Booking date (col 0) intentionally differs from operation date
(col 1) so "prefer operation date" is exercised.

**Acceptance (asserted to the grosz):**
| detected | value |
|---|---|
| encoding | `windows-1250` |
| delimiter | `;` |
| headerRowIndex (parsed) | `5` |
| dateIndex / format | `1` / `yyyy-MM-dd` (operation date, **not** the col-0 booking date) |
| amountMode / index / sign | `SIGNED` / `6` / `expenseIsNegative=true` |
| descriptionIndexes | `[2, 3]` (Opis + Tytuł, space-joined) |
| Saldo (col 7) | **never** chosen for any role |
| preview | `incomeMinor=517500`, `expenseMinor=194969`, 10 valid, 0 duplicate |
| commit | 10 transactions: 7 expense (194969), 3 income (517500); footer rows skipped as invalid |

Per-user isolation test included (another user cannot preview/commit against this account — 404).

---

## 8. Build & rollout order (phase-gated, `CLAUDE.md` §17)

Branch `backlog-smart-csv-import` off `main`; backend then frontend; commit **only when asked**.

1. **Phase A — backend detection.** Fixture → `EncodingDetector`/`HeaderDetector`/`MappingDetector`
   (+ unit tests) → DTO/entity/migration V16 → `ImportRowBuilder`/`ImportService`/`ImportController`
   wiring → `ImportSmartDetectionTest` (fixture oracle + isolation) → `./gradlew build` green →
   commit `feat(backend): smart CSV import — encoding/header/column auto-detection (V16)`. **Stop at
   the boundary** for in-app testing.
2. **Phase B — frontend detect-first.** Types regen → detect-first `ImportPage` + banner + totals +
   "Adjust columns" expander → i18n → `npm run lint && npm test && npm run build` green → commit
   `feat(frontend): detect-first CSV import`.
3. One-off throwaway check (drop the fixture-style file → auto-mapped preview → commit); delete it.
4. Update `HANDOFF.md` + `CLAUDE.md` (local-only). Next backlog item resumes at **E — saved views/filters**.

---

## 9. Out of scope (YAGNI)

- Per-bank presets / bank-logo recognition (dictionary-only, by design).
- Auto-detecting **transfers** (imports remain expense/income only, per `ParsedImportRow`).
- Multi-file / XLSX / OFX/MT940 import.
- Learning/correcting the dictionary from user edits (a possible follow-up: persist per-account
  overrides — already covered by the existing `ImportProfile` round-trip).
- Changing the dedupe hash, batch/undo model, or the rules-engine auto-categorization.
