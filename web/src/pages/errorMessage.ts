import type { TFunction } from 'i18next';

import { ApiError } from '../api/problem';
import { errorKey, translateKey } from '../i18n/messages';

/** pt-BR message for any error thrown by an API call. */
export function errorMessage(t: TFunction, error: unknown): string {
  const key = error instanceof ApiError ? errorKey(error.code) : 'errors.generic';
  return translateKey(t, key) ?? t('errors.generic');
}
