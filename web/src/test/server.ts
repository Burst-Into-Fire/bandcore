import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/** Builds an absolute API URL, as the app's client does from window.location. */
export const apiUrl = (path: string) => new URL(`/api${path}`, window.location.origin).href;

/** Problem Details body as the API sends it. */
export function problem(
  status: number,
  code: string,
  errors?: { field: string; code: string; message: string }[],
) {
  return HttpResponse.json(
    { status, code, title: 'Error', ...(errors ? { errors } : {}) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** Default handlers: the API is up and there is no session cookie. Override with server.use(...). */
export const handlers = [
  http.get(apiUrl('/status'), () => HttpResponse.json({ status: 'UP' })),
  http.post(apiUrl('/auth/refresh'), () => problem(401, 'UNAUTHENTICATED')),
];

export const server = setupServer(...handlers);
