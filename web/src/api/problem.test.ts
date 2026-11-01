import { describe, expect, it, vi } from 'vitest';

import { applyProblem } from './forms';
import { ApiError, toApiError } from './problem';

const response = (status: number) => new Response(null, { status });

describe('toApiError', () => {
  it('reads the code and field errors of a Problem Details body', () => {
    const error = toApiError(
      {
        status: 422,
        code: 'PASSWORD_TOO_COMMON',
        errors: [{ field: 'password', code: 'PASSWORD_TOO_COMMON', message: 'x' }, { bad: true }],
      },
      response(422),
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(422);
    expect(error.code).toBe('PASSWORD_TOO_COMMON');
    expect(error.fieldErrors).toEqual([
      { field: 'password', code: 'PASSWORD_TOO_COMMON', message: 'x' },
    ]);
  });

  it('survives bodies that are not Problem Details', () => {
    expect(toApiError('Bad gateway', response(502)).code).toBe('UNKNOWN');
    expect(toApiError(undefined, response(500)).fieldErrors).toEqual([]);
  });
});

describe('applyProblem', () => {
  it('puts field errors on their fields as i18n keys', () => {
    const setError = vi.fn();

    applyProblem(
      new ApiError(400, 'VALIDATION_FAILED', [
        { field: 'email', code: 'Email', message: '' },
        { field: 'displayName', code: 'NotBlank', message: '' },
      ]),
      setError,
      ['email', 'displayName'],
    );

    expect(setError).toHaveBeenCalledWith('email', {
      type: 'server',
      message: 'validation.emailInvalid',
    });
    expect(setError).toHaveBeenCalledWith('displayName', {
      type: 'server',
      message: 'validation.required',
    });
  });

  it('shows other errors for the whole form', () => {
    const setError = vi.fn();

    applyProblem(new ApiError(401, 'INVALID_CREDENTIALS'), setError, ['email']);
    applyProblem(new ApiError(500, 'INTERNAL_ERROR'), setError, ['email']);
    applyProblem(new TypeError('Failed to fetch'), setError, ['email']);

    expect(setError.mock.calls).toEqual([
      ['root.server', { type: 'server', message: 'errors.INVALID_CREDENTIALS' }],
      ['root.server', { type: 'server', message: 'errors.generic' }],
      ['root.server', { type: 'server', message: 'errors.generic' }],
    ]);
  });
});
