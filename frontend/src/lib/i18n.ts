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
      nav: { dashboard: 'Dashboard', accounts: 'Accounts', transactions: 'Transactions', settings: 'Settings', logout: 'Log out' },
      common: {
        save: 'Save', cancel: 'Cancel', create: 'Create', edit: 'Edit', delete: 'Delete',
        archive: 'Archive', loading: 'Loading…', retry: 'Retry', all: 'All', none: 'None',
        confirm: 'Confirm', actions: 'Actions', optional: 'optional',
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
        expense: 'Expense', income: 'Income', transfer: 'Transfer', filters: 'Filters',
        deleteConfirm: 'Delete this transaction?', from: 'From', to: 'To', rateHint: 'Required for currencies other than your base.',
      },
      settings: {
        title: 'Settings', reportingCurrency: 'Reporting currency',
        reportingHint: 'Totals and reports roll up into this currency. Changing it affects future display only — locked per-transaction rates are never rewritten.',
        saved: 'Settings saved.',
      },
    },
  },
  pl: {
    translation: {
      app: { name: 'Finanse', tagline: 'Zobacz, gdzie znikają Twoje pieniądze' },
      nav: { dashboard: 'Pulpit', accounts: 'Konta', transactions: 'Transakcje', settings: 'Ustawienia', logout: 'Wyloguj' },
      common: {
        save: 'Zapisz', cancel: 'Anuluj', create: 'Utwórz', edit: 'Edytuj', delete: 'Usuń',
        archive: 'Archiwizuj', loading: 'Ładowanie…', retry: 'Ponów', all: 'Wszystkie', none: 'Brak',
        confirm: 'Potwierdź', actions: 'Akcje', optional: 'opcjonalne',
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
        expense: 'Wydatek', income: 'Przychód', transfer: 'Przelew', filters: 'Filtry',
        deleteConfirm: 'Usunąć tę transakcję?', from: 'Od', to: 'Do', rateHint: 'Wymagany dla walut innych niż bazowa.',
      },
      settings: {
        title: 'Ustawienia', reportingCurrency: 'Waluta raportowania',
        reportingHint: 'Sumy i raporty są przeliczane na tę walutę. Zmiana wpływa tylko na przyszłe wyświetlanie — zablokowane kursy transakcji nie są nadpisywane.',
        saved: 'Zapisano ustawienia.',
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
