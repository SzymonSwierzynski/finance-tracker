import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

/**
 * i18n layer (CLAUDE.md §2). All user-facing chrome flows through these bundles; pl-PL is the
 * project's primary locale, en is the fallback. Money/date formatting derives its locale from the
 * active language via {@link localeForLanguage} — symbols/formats are never hard-coded.
 */
const resources = {
  en: {
    translation: {
      app: { name: 'Finance Tracker', tagline: 'See where your money goes' },
      nav: { dashboard: 'Dashboard', breakdown: 'Breakdown', trends: 'Trends', transactions: 'Transactions', import: 'Import', rules: 'Rules', accounts: 'Accounts', categories: 'Categories', settings: 'Settings', logout: 'Log out' },
      common: {
        save: 'Save', cancel: 'Cancel', create: 'Create', edit: 'Edit', delete: 'Delete',
        archive: 'Archive', loading: 'Loading…', retry: 'Retry', all: 'All', none: 'None',
        confirm: 'Confirm', actions: 'Actions', optional: 'optional', yes: 'Yes', no: 'No',
      },
      errors: { generic: 'Something went wrong.', loadFailed: 'Could not load data.', required: 'Required' },
      auth: {
        login: 'Log in', register: 'Create account', email: 'Email', password: 'Password',
        displayName: 'Display name', haveAccount: 'Already have an account?', noAccount: "Don't have an account?",
        loginCta: 'Log in', registerCta: 'Sign up', welcome: 'Welcome back', startNow: 'Track your money',
      },
      dashboard: {
        title: 'Dashboard', income: 'Income', expense: 'Expense', net: 'Net', period: 'Period',
        thisMonth: 'This month', lastMonth: 'Last month', thisYear: 'This year', custom: 'Custom',
        recent: 'Recent transactions', incomeVsExpense: 'Income vs expense', empty: 'No activity in this period yet.',
      },
      accounts: {
        title: 'Accounts', new: 'New account', name: 'Name', type: 'Type', currency: 'Currency',
        trackBalance: 'Track balance', startingBalance: 'Starting balance', archived: 'Archived',
        showArchived: 'Show archived', balance: 'Balance', empty: 'No accounts yet — add your first one.',
        checking: 'Checking', savings: 'Savings', cash: 'Cash', credit: 'Credit',
        archiveConfirm: 'Archive this account? It will be hidden from lists.',
      },
      transactions: {
        title: 'Transactions', new: 'New transaction', date: 'Date', amount: 'Amount', type: 'Type',
        account: 'Account', counterAccount: 'To account', description: 'Description', note: 'Note',
        rate: 'Rate to base', baseValue: 'Base value', empty: 'No transactions match these filters.',
        expense: 'Expense', income: 'Income', transfer: 'Transfer', filters: 'Filters', category: 'Category', search: 'Search description…',
        deleteConfirm: 'Delete this transaction?', from: 'From', to: 'To', rateHint: 'Leave blank to use the rate from Settings. Whatever is used gets locked onto this transaction.',
      },
      categories: {
        title: 'Categories', new: 'New category', name: 'Name', kind: 'Kind', color: 'Color',
        parent: 'Parent category', topLevel: 'Top-level (no parent)', expense: 'Expense', income: 'Income',
        empty: 'No categories yet — add your first one.', deleteConfirm: 'Delete this category? Its subcategories are removed and affected transactions become uncategorized.',
        deleted: 'Category deleted ({{count}} transaction(s) uncategorized).',
      },
      rules: {
        title: 'Rules', subtitle: 'Auto-categorize transactions whose description matches a pattern.',
        new: 'New rule', pattern: 'Pattern', category: 'Category', priority: 'Priority',
        apply: 'Apply to uncategorized', applied: '{{categorized}} of {{scanned}} transaction(s) categorized.',
        patternHint: 'Matched as a case-insensitive substring of the description. Higher priority wins.',
        empty: 'No rules yet — add one to auto-categorize transactions.', deleteConfirm: 'Delete this rule?',
      },
      import: {
        title: 'Import', subtitle: 'Import a CSV export from your bank.',
        account: 'Account', file: 'CSV file', next: 'Next', back: 'Back', preview: 'Preview', commit: 'Import',
        step1: 'Account & file', step2: 'Map columns', step3: 'Preview',
        encoding: 'Encoding', delimiter: 'Delimiter', autoDetect: 'Auto-detect', hasHeader: 'First row is a header',
        dateColumn: 'Date column', dateFormat: 'Date format', descriptionColumn: 'Description column',
        amountMode: 'Amount columns', signed: 'One signed column', debitCredit: 'Separate debit/credit',
        amountColumn: 'Amount column', expenseIsNegative: 'Negative = expense', debitColumn: 'Debit column', creditColumn: 'Credit column',
        rows: '{{count}} rows', valid: '{{count}} to import', duplicates: '{{count}} duplicate', invalid: '{{count}} invalid',
        duplicate: 'Duplicate', invalidRow: 'Invalid', misdecoded: 'Some characters look wrong — try a different encoding.',
        imported: 'Imported {{imported}} — skipped {{duplicates}} duplicate(s), {{invalid}} invalid.',
        batches: 'Import history', undo: 'Undo', undone: 'Import undone ({{count}} transaction(s) removed).',
        undoConfirm: 'Undo this import? Its {{count}} transaction(s) will be deleted.', noBatches: 'No imports yet.',
      },
      breakdown: {
        title: 'Breakdown', categories: 'Categories', uncategorized: 'Uncategorized',
        allCategories: 'All categories', viewTransactions: 'View transactions', empty: 'No spending in this period yet.',
      },
      trends: {
        title: 'Trends', total: 'Total', category: 'By category', month: 'Monthly', week: 'Weekly',
        income: 'Income', expense: 'Expense', net: 'Running net', empty: 'No activity in this period yet.',
      },
      settings: {
        title: 'Settings', reportingCurrency: 'Reporting currency',
        reportingHint: 'Totals and reports roll up into this currency. Changing it affects future display only — locked per-transaction rates are never rewritten.',
        saved: 'Settings saved.',
      },
      fx: {
        title: 'Exchange rates',
        hint: 'Rates convert other currencies into your reporting currency. A transaction copies the rate when you save it and keeps it forever, so editing a rate here only affects what you enter next.',
        currency: 'Currency', rate: 'Rate', addRate: 'Add rate', saved: 'Rate saved.', deleted: 'Rate removed.',
        empty: 'No exchange rates yet. Add one for each currency you hold.',
        example: '1 {{currency}} = {{rate}} {{base}}',
        stale: 'Anchored to {{base}}',
        staleHint: 'This rate was saved against {{base}}, which is no longer your reporting currency. Re-enter it before adding transactions in this currency.',
        removeConfirm: 'Remove the rate for {{currency}}?',
        sameAsBase: 'This is already your reporting currency.',
      },
    },
  },
  pl: {
    translation: {
      app: { name: 'Finanse', tagline: 'Zobacz, gdzie znikają Twoje pieniądze' },
      nav: { dashboard: 'Pulpit', breakdown: 'Podział', trends: 'Trendy', transactions: 'Transakcje', import: 'Import', rules: 'Reguły', accounts: 'Konta', categories: 'Kategorie', settings: 'Ustawienia', logout: 'Wyloguj' },
      common: {
        save: 'Zapisz', cancel: 'Anuluj', create: 'Utwórz', edit: 'Edytuj', delete: 'Usuń',
        archive: 'Archiwizuj', loading: 'Ładowanie…', retry: 'Ponów', all: 'Wszystkie', none: 'Brak',
        confirm: 'Potwierdź', actions: 'Akcje', optional: 'opcjonalne', yes: 'Tak', no: 'Nie',
      },
      errors: { generic: 'Coś poszło nie tak.', loadFailed: 'Nie udało się wczytać danych.', required: 'Wymagane' },
      auth: {
        login: 'Zaloguj się', register: 'Załóż konto', email: 'E-mail', password: 'Hasło',
        displayName: 'Nazwa', haveAccount: 'Masz już konto?', noAccount: 'Nie masz konta?',
        loginCta: 'Zaloguj', registerCta: 'Zarejestruj', welcome: 'Witaj ponownie', startNow: 'Śledź swoje finanse',
      },
      dashboard: {
        title: 'Pulpit', income: 'Przychody', expense: 'Wydatki', net: 'Saldo', period: 'Okres',
        thisMonth: 'Ten miesiąc', lastMonth: 'Poprzedni miesiąc', thisYear: 'Ten rok', custom: 'Własny',
        recent: 'Ostatnie transakcje', incomeVsExpense: 'Przychody i wydatki', empty: 'Brak aktywności w tym okresie.',
      },
      accounts: {
        title: 'Konta', new: 'Nowe konto', name: 'Nazwa', type: 'Typ', currency: 'Waluta',
        trackBalance: 'Śledź saldo', startingBalance: 'Saldo początkowe', archived: 'Zarchiwizowane',
        showArchived: 'Pokaż zarchiwizowane', balance: 'Saldo', empty: 'Brak kont — dodaj pierwsze.',
        checking: 'Osobiste', savings: 'Oszczędnościowe', cash: 'Gotówka', credit: 'Kredytowe',
        archiveConfirm: 'Zarchiwizować to konto? Zniknie z list.',
      },
      transactions: {
        title: 'Transakcje', new: 'Nowa transakcja', date: 'Data', amount: 'Kwota', type: 'Typ',
        account: 'Konto', counterAccount: 'Konto docelowe', description: 'Opis', note: 'Notatka',
        rate: 'Kurs do bazy', baseValue: 'Wartość bazowa', empty: 'Brak transakcji dla tych filtrów.',
        expense: 'Wydatek', income: 'Przychód', transfer: 'Przelew', filters: 'Filtry', category: 'Kategoria', search: 'Szukaj w opisie…',
        deleteConfirm: 'Usunąć tę transakcję?', from: 'Od', to: 'Do', rateHint: 'Zostaw puste, aby użyć kursu z Ustawień. Użyty kurs zostanie zapisany w tej transakcji.',
      },
      categories: {
        title: 'Kategorie', new: 'Nowa kategoria', name: 'Nazwa', kind: 'Rodzaj', color: 'Kolor',
        parent: 'Kategoria nadrzędna', topLevel: 'Najwyższy poziom (bez nadrzędnej)', expense: 'Wydatki', income: 'Przychody',
        empty: 'Brak kategorii — dodaj pierwszą.', deleteConfirm: 'Usunąć tę kategorię? Podkategorie zostaną usunięte, a powiązane transakcje stracą kategorię.',
        deleted: 'Usunięto kategorię ({{count}} transakcji bez kategorii).',
      },
      rules: {
        title: 'Reguły', subtitle: 'Automatyczne kategoryzowanie transakcji pasujących do wzorca.',
        new: 'Nowa reguła', pattern: 'Wzorzec', category: 'Kategoria', priority: 'Priorytet',
        apply: 'Zastosuj do bez kategorii', applied: 'Skategoryzowano {{categorized}} z {{scanned}} transakcji.',
        patternHint: 'Dopasowywany jako fragment opisu (bez wielkości liter). Wyższy priorytet wygrywa.',
        empty: 'Brak reguł — dodaj regułę, aby automatycznie kategoryzować.', deleteConfirm: 'Usunąć tę regułę?',
      },
      import: {
        title: 'Import', subtitle: 'Zaimportuj plik CSV z banku.',
        account: 'Konto', file: 'Plik CSV', next: 'Dalej', back: 'Wstecz', preview: 'Podgląd', commit: 'Importuj',
        step1: 'Konto i plik', step2: 'Mapuj kolumny', step3: 'Podgląd',
        encoding: 'Kodowanie', delimiter: 'Separator', autoDetect: 'Wykryj automatycznie', hasHeader: 'Pierwszy wiersz to nagłówek',
        dateColumn: 'Kolumna daty', dateFormat: 'Format daty', descriptionColumn: 'Kolumna opisu',
        amountMode: 'Kolumny kwoty', signed: 'Jedna kolumna ze znakiem', debitCredit: 'Osobne obciążenie/uznanie',
        amountColumn: 'Kolumna kwoty', expenseIsNegative: 'Ujemne = wydatek', debitColumn: 'Kolumna obciążenia', creditColumn: 'Kolumna uznania',
        rows: '{{count}} wierszy', valid: '{{count}} do importu', duplicates: '{{count}} duplikat(ów)', invalid: '{{count}} błędnych',
        duplicate: 'Duplikat', invalidRow: 'Błędny', misdecoded: 'Niektóre znaki wyglądają źle — wybierz inne kodowanie.',
        imported: 'Zaimportowano {{imported}} — pominięto {{duplicates}} duplikat(ów), {{invalid}} błędnych.',
        batches: 'Historia importów', undo: 'Cofnij', undone: 'Cofnięto import (usunięto {{count}} transakcji).',
        undoConfirm: 'Cofnąć ten import? Zostanie usuniętych {{count}} transakcji.', noBatches: 'Brak importów.',
      },
      breakdown: {
        title: 'Podział', categories: 'Kategorie', uncategorized: 'Bez kategorii',
        allCategories: 'Wszystkie kategorie', viewTransactions: 'Pokaż transakcje', empty: 'Brak wydatków w tym okresie.',
      },
      trends: {
        title: 'Trendy', total: 'Razem', category: 'Wg kategorii', month: 'Miesięcznie', week: 'Tygodniowo',
        income: 'Przychody', expense: 'Wydatki', net: 'Saldo skumulowane', empty: 'Brak aktywności w tym okresie.',
      },
      settings: {
        title: 'Ustawienia', reportingCurrency: 'Waluta raportowania',
        reportingHint: 'Sumy i raporty są przeliczane na tę walutę. Zmiana wpływa tylko na przyszłe wyświetlanie — zablokowane kursy transakcji nie są nadpisywane.',
        saved: 'Zapisano ustawienia.',
      },
      fx: {
        title: 'Kursy walut',
        hint: 'Kursy przeliczają inne waluty na walutę raportowania. Transakcja zapisuje kurs w momencie dodania i zachowuje go na zawsze, więc zmiana kursu tutaj wpływa tylko na kolejne wpisy.',
        currency: 'Waluta', rate: 'Kurs', addRate: 'Dodaj kurs', saved: 'Zapisano kurs.', deleted: 'Usunięto kurs.',
        empty: 'Brak kursów walut. Dodaj kurs dla każdej posiadanej waluty.',
        example: '1 {{currency}} = {{rate}} {{base}}',
        stale: 'Kurs względem {{base}}',
        staleHint: 'Ten kurs zapisano względem {{base}}, która nie jest już walutą raportowania. Wprowadź go ponownie przed dodaniem transakcji w tej walucie.',
        removeConfirm: 'Usunąć kurs dla {{currency}}?',
        sameAsBase: 'To już jest Twoja waluta raportowania.',
      },
    },
  },
}

const STORAGE_KEY = 'ft-lang'

function readStoredLang(): string | null {
  try {
    if (typeof localStorage !== 'undefined' && typeof localStorage.getItem === 'function') {
      return localStorage.getItem(STORAGE_KEY)
    }
  } catch {
    /* storage unavailable (private mode, test env) */
  }
  return null
}

void i18n.use(initReactI18next).init({
  resources,
  lng: readStoredLang() ?? 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (lng) => {
  try {
    if (typeof localStorage !== 'undefined' && typeof localStorage.setItem === 'function') {
      localStorage.setItem(STORAGE_KEY, lng)
    }
  } catch {
    /* ignore */
  }
})

export function localeForLanguage(language: string): string {
  return language.startsWith('pl') ? 'pl-PL' : 'en-GB'
}

export default i18n
