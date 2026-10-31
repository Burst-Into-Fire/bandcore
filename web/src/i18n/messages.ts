import type { TFunction } from 'i18next';

/**
 * Form messages are stored as i18n keys (zod schemas and server errors alike) and translated
 * when shown, so every message goes through the catalog.
 */

/** Error codes the API returns that have their own message; anything else is generic. */
const ERROR_CODES = [
  'VALIDATION_FAILED',
  'INVALID_CREDENTIALS',
  'PASSWORD_TOO_SHORT',
  'PASSWORD_TOO_LONG',
  'PASSWORD_TOO_COMMON',
  'TOKEN_INVALID',
  'SLUG_INVALID',
  'SLUG_RESERVED',
  'SLUG_TAKEN',
  'EMAIL_NOT_VERIFIED',
  'BAND_LIMIT_REACHED',
  'TOO_MANY_REQUESTS',
] as const;

/** Bean Validation codes in `errors[].code` of a VALIDATION_FAILED problem. */
const FIELD_CODES: Record<string, string> = {
  NotBlank: 'validation.required',
  NotNull: 'validation.required',
  Email: 'validation.emailInvalid',
};

/** i18n key for an API error code. */
export function errorKey(code: string): string {
  return (ERROR_CODES as readonly string[]).includes(code) ? `errors.${code}` : 'errors.generic';
}

/** i18n key for a field error returned by the API. */
export function fieldErrorKey(code: string): string {
  return FIELD_CODES[code] ?? errorKey(code);
}

/** Translates a stored key; unknown keys show the generic message, never the raw key. */
export function translateKey(t: TFunction, key: string | undefined): string | undefined {
  if (!key) {
    return undefined;
  }
  const translate = t as unknown as (key: string, options: { defaultValue: string }) => string;
  return translate(key, { defaultValue: t('errors.generic') });
}
