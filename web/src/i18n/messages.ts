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
  'TIMEZONE_INVALID',
  'MEMBER_HAS_HISTORY',
  'USER_NOT_IN_BAND',
  'ACCOUNT_ALREADY_LINKED',
  'MEMBER_ALREADY_LINKED',
  'ALREADY_A_MEMBER',
  'INVITATION_PENDING',
  'INVITATION_EMAIL_MISMATCH',
  'LAST_ADMIN',
  'NOT_FOUND',
  'ACCESS_DENIED',
  'CATEGORY_NAME_TAKEN',
  'CATEGORY_IN_USE',
  'CATEGORY_ARCHIVED',
  'PAYER_REQUIRED',
  'MEMBER_INACTIVE',
  'EXPENSE_NOT_PAID',
  'EXPENSE_NOT_PLANNED',
  'VERSION_CONFLICT',
  'SPLIT_SUM_MISMATCH',
  'SPLIT_PERCENT_SUM',
  'SPLIT_INVALID',
  'SETTLEMENT_SAME_MEMBER',
  'PRODUCT_NAME_TAKEN',
  'VARIANT_TAKEN',
  'SKU_TAKEN',
  'PRODUCT_HAS_HISTORY',
  'VARIANT_HAS_HISTORY',
  'LAST_VARIANT',
  'PRODUCT_CURRENCY_LOCKED',
  'INSUFFICIENT_STOCK',
  'VARIANT_ARCHIVED',
  'BATCH_CURRENCY_MISMATCH',
  'BATCH_DUPLICATE_VARIANT',
  'BATCH_HAS_LATER_MOVEMENTS',
  'BATCH_CANCELLED',
  'BATCH_COST_REQUIRED',
  'STOCK_UNCHANGED',
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
