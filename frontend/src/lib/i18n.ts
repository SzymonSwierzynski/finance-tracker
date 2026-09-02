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
      nav: { dashboard: 'Dashboard', breakdown: 'Breakdown', trends: 'Trends', transactions: 'Transactions', import: 'Import', rules: 'Rules', recurring: 'Recurring', budgets: 'Budgets', accounts: 'Accounts', categories: 'Categories', settings: 'Settings', logout: 'Log out' },
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
        compare: 'Compare', compare_off: 'Off', compare_month: 'MoM', compare_year: 'YoY',
        vsLastMonth: 'vs last month', vsLastYear: 'vs last year',
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
        deleted: 'Transaction deleted', undo: 'Undo', restored: 'Transaction restored', trash: 'Trash', trashTitle: 'Deleted transactions', trashEmpty: 'Trash is empty.', restore: 'Restore', deleteForever: 'Delete forever', deleteForeverConfirm: 'Permanently delete this transaction? This cannot be undone.',
        selectedCount: '{{count}} selected', selectAll: 'Select all', selectRow: 'Select row', recategorize: 'Recategorize', recategorizeHint: 'Select transactions of a single kind (no transfers) to recategorize.', uncategorize: 'Uncategorize', bulkDeleted: '{{count}} deleted',
      },
      categories: {
        title: 'Categories', new: 'New category', name: 'Name', kind: 'Kind', color: 'Color',
        parent: 'Parent category', topLevel: 'Top-level (no parent)', expense: 'Expense', income: 'Income',
        expand: 'Expand', collapse: 'Collapse', expandAll: 'Expand all', collapseAll: 'Collapse all',
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
      recurring: {
        title: 'Recurring', subtitle: 'Templates that create transactions on a schedule.',
        new: 'New recurring', run: 'Run due now', ran: 'Created {{count}} transaction(s) from due templates.',
        empty: 'No recurring templates yet.', deleteConfirm: 'Delete this recurring template?',
        frequency: 'Frequency', every: 'Every (N)', startDate: 'Starts', endDate: 'Ends', next: 'Next',
        daily: 'Daily', weekly: 'Weekly', monthly: 'Monthly', yearly: 'Yearly',
        pause: 'Pause', resume: 'Resume', paused: 'Paused',
      },
      budgets: {
        title: 'Budgets', subtitle: 'Set a monthly limit per category and track spending against it.',
        new: 'New budget', edit: 'Edit budget', empty: 'No budgets yet — add one to track a category.',
        deleteConfirm: 'Delete this budget?', month: 'Month', category: 'Category', monthlyLimit: 'Monthly limit',
        over: 'Over', left: 'Left', invalidAmount: 'Enter an amount greater than zero.',
        rollover: 'Roll over unused budget', rolloverHelp: 'Unspent amount carries into next month; a heavier month can draw it down, but it never goes below zero.', rolloverBadge: 'Rollover', carried: 'carried',
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
        detected: 'Detected', headerRow: 'header row {{row}}', adjustColumns: 'Adjust columns',
        income: 'Income', expense: 'Expense',
        role_date: 'date', role_amount: 'amount', role_description: 'description', role_debit: 'debit', role_credit: 'credit',
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
        compare: 'Compare to previous period', vsPrev: 'vs prev', expenseTotal: 'Expenses', netTotal: 'Net',
        moversTitle: 'Category movers · expenses vs previous', moverNew: 'new', moverGone: 'gone',
        noMovers: 'No comparable spending in either period.',
        highlight: 'Biggest mover: {{name}} ({{change}}) vs the previous period.',
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
      export: {
        title: 'Export data',
        hint: 'Export your transactions or a full backup (accounts, categories and every transaction). Money stays as integer minor units, so it round-trips losslessly. Restoring a backup merges it into your current data.',
        csv: 'Transactions CSV', json: 'Transactions JSON', backup: 'Full backup', restore: 'Restore backup',
        restored: 'Restored {{transactions}} transaction(s), {{accounts}} account(s), {{categories}} category(ies); skipped {{skipped}}.',
        restoreInvalid: 'That file is not a valid backup.',
      },
    },
  },
  pl: {
    translation: {
      app: { name: 'Finanse', tagline: 'Zobacz, gdzie znikają Twoje pieniądze' },
      nav: { dashboard: 'Pulpit', breakdown: 'Podział', trends: 'Trendy', transactions: 'Transakcje', import: 'Import', rules: 'Reguły', recurring: 'Cykliczne', budgets: 'Budżety', accounts: 'Konta', categories: 'Kategorie', settings: 'Ustawienia', logout: 'Wyloguj' },
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
        compare: 'Porównaj', compare_off: 'Wył.', compare_month: 'm/m', compare_year: 'r/r',
        vsLastMonth: 'wzgl. poprz. miesiąca', vsLastYear: 'wzgl. poprz. roku',
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
        deleted: 'Usunięto transakcję', undo: 'Cofnij', restored: 'Przywrócono transakcję', trash: 'Kosz', trashTitle: 'Usunięte transakcje', trashEmpty: 'Kosz jest pusty.', restore: 'Przywróć', deleteForever: 'Usuń trwale', deleteForeverConfirm: 'Trwale usunąć tę transakcję? Tej operacji nie można cofnąć.',
        selectedCount: 'Zaznaczono: {{count}}', selectAll: 'Zaznacz wszystkie', selectRow: 'Zaznacz wiersz', recategorize: 'Zmień kategorię', recategorizeHint: 'Wybierz transakcje jednego rodzaju (bez przelewów), aby zmienić kategorię.', uncategorize: 'Usuń kategorię', bulkDeleted: 'Usunięto: {{count}}',
      },
      categories: {
        title: 'Kategorie', new: 'Nowa kategoria', name: 'Nazwa', kind: 'Rodzaj', color: 'Kolor',
        parent: 'Kategoria nadrzędna', topLevel: 'Najwyższy poziom (bez nadrzędnej)', expense: 'Wydatki', income: 'Przychody',
        expand: 'Rozwiń', collapse: 'Zwiń', expandAll: 'Rozwiń wszystko', collapseAll: 'Zwiń wszystko',
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
      recurring: {
        title: 'Cykliczne', subtitle: 'Szablony tworzące transakcje według harmonogramu.',
        new: 'Nowy szablon', run: 'Uruchom teraz', ran: 'Utworzono {{count}} transakcji z szablonów.',
        empty: 'Brak szablonów cyklicznych.', deleteConfirm: 'Usunąć ten szablon cykliczny?',
        frequency: 'Częstotliwość', every: 'Co (N)', startDate: 'Początek', endDate: 'Koniec', next: 'Następne',
        daily: 'Codziennie', weekly: 'Co tydzień', monthly: 'Co miesiąc', yearly: 'Co rok',
        pause: 'Wstrzymaj', resume: 'Wznów', paused: 'Wstrzymane',
      },
      budgets: {
        title: 'Budżety', subtitle: 'Ustaw miesięczny limit dla kategorii i śledź wydatki względem niego.',
        new: 'Nowy budżet', edit: 'Edytuj budżet', empty: 'Brak budżetów — dodaj, aby śledzić kategorię.',
        deleteConfirm: 'Usunąć ten budżet?', month: 'Miesiąc', category: 'Kategoria', monthlyLimit: 'Limit miesięczny',
        over: 'Ponad', left: 'Zostało', invalidAmount: 'Podaj kwotę większą od zera.',
        rollover: 'Przenoś niewykorzystany budżet', rolloverHelp: 'Niewydana kwota przechodzi na kolejny miesiąc; cięższy miesiąc może ją uszczuplić, ale nie zejdzie poniżej zera.', rolloverBadge: 'Przeniesienie', carried: 'przeniesione',
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
        detected: 'Wykryto', headerRow: 'wiersz nagłówka {{row}}', adjustColumns: 'Dostosuj kolumny',
        income: 'Przychód', expense: 'Wydatek',
        role_date: 'data', role_amount: 'kwota', role_description: 'opis', role_debit: 'obciążenie', role_credit: 'uznanie',
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
        compare: 'Porównaj z poprzednim okresem', vsPrev: 'wzgl. poprz.', expenseTotal: 'Wydatki', netTotal: 'Saldo',
        moversTitle: 'Zmiany kategorii · wydatki wzgl. poprzedniego', moverNew: 'nowa', moverGone: 'brak',
        noMovers: 'Brak porównywalnych wydatków w obu okresach.',
        highlight: 'Największa zmiana: {{name}} ({{change}}) wzgl. poprzedniego okresu.',
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
      export: {
        title: 'Eksport danych',
        hint: 'Wyeksportuj transakcje lub pełną kopię (konta, kategorie i wszystkie transakcje). Kwoty pozostają liczbami całkowitymi (grosze), więc dane można bezstratnie zaimportować. Przywrócenie kopii scala ją z bieżącymi danymi.',
        csv: 'Transakcje CSV', json: 'Transakcje JSON', backup: 'Pełna kopia', restore: 'Przywróć kopię',
        restored: 'Przywrócono: {{transactions}} transakcji, {{accounts}} kont, {{categories}} kategorii; pominięto {{skipped}}.',
        restoreInvalid: 'Ten plik nie jest prawidłową kopią.',
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
