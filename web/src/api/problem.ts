/** RFC 9457 Problem Details as returned by the API, with its stable `code`. */
export interface FieldProblem {
  field: string;
  code: string;
  message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldProblem[];

  constructor(status: number, code: string, fieldErrors: FieldProblem[] = []) {
    super(`API error ${status} ${code}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** Builds an ApiError from an openapi-fetch error body and the response. */
export function toApiError(error: unknown, response: Response): ApiError {
  if (!isRecord(error)) {
    return new ApiError(response.status, 'UNKNOWN');
  }
  const code = typeof error.code === 'string' ? error.code : 'UNKNOWN';
  const fieldErrors = Array.isArray(error.errors)
    ? error.errors.filter(
        (item): item is FieldProblem =>
          isRecord(item) && typeof item.field === 'string' && typeof item.code === 'string',
      )
    : [];
  return new ApiError(response.status, code, fieldErrors);
}

/** The data of a successful openapi-fetch call, or throws an ApiError. */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (!result.response.ok) {
    throw toApiError(result.error, result.response);
  }
  return result.data as T;
}
