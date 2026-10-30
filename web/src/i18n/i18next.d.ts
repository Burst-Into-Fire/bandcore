import type ptBR from './locales/pt-BR.json';

// Type-checks translation keys: t('home.title') compiles, t('home.typo') does not.
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation';
    resources: { translation: typeof ptBR };
  }
}
