import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

import { errorKey, fieldErrorKey } from '../i18n/messages';
import { ApiError } from './problem';

/**
 * Shows an API error in a form: field errors next to their fields, anything else as a message
 * for the whole form (`errors.root.server`). Messages are i18n keys.
 */
export function applyProblem<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): void {
  if (!(error instanceof ApiError)) {
    setError('root.server', { type: 'server', message: 'errors.generic' });
    return;
  }
  let mapped = false;
  for (const fieldError of error.fieldErrors) {
    const field = fields.find((name) => name === fieldError.field);
    if (field) {
      setError(field, { type: 'server', message: fieldErrorKey(fieldError.code) });
      mapped = true;
    }
  }
  if (!mapped) {
    setError('root.server', { type: 'server', message: errorKey(error.code) });
  }
}

/**
 * The same error with list fields named as react-hook-form names them: the API says
 * `items[0].quantity`, a form field array says `items.0.quantity`.
 */
export function withFormPaths(error: unknown): unknown {
  if (!(error instanceof ApiError)) {
    return error;
  }
  return new ApiError(
    error.status,
    error.code,
    error.fieldErrors.map((fieldError) => ({
      ...fieldError,
      field: fieldError.field.replace(/\[(\d+)\]/g, '.$1'),
    })),
  );
}
