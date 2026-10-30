import createClient from 'openapi-fetch';

import { CSRF_HEADER, CSRF_VALUE, getAccessToken, refreshSession } from '../auth/session';
import type { paths } from './schema';

/** Endpoints that never take an access token, so a 401 there must not trigger a refresh. */
const SESSIONLESS_PATHS = [
  '/api/auth/login',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/signup',
  '/api/auth/verify-email',
  '/api/auth/resend-verification',
];

function isSessionless(url: string): boolean {
  const { pathname } = new URL(url);
  return SESSIONLESS_PATHS.includes(pathname);
}

function send(request: Request): Promise<Response> {
  const headers = new Headers(request.headers);
  headers.set(CSRF_HEADER, CSRF_VALUE);
  const token = getAccessToken();
  if (token && !isSessionless(request.url)) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  // Resolve fetch on every call, so test interceptors installed later still apply.
  return globalThis.fetch(new Request(request, { headers }));
}

/**
 * Adds the access token and the CSRF header. On a 401, refreshes the session once (shared with
 * every other request that got a 401 at the same time) and retries the request once.
 */
export async function sessionFetch(request: Request): Promise<Response> {
  const retry = request.clone();
  const response = await send(request);
  if (response.status !== 401 || isSessionless(request.url)) {
    return response;
  }
  const refreshed = await refreshSession();
  return refreshed ? send(retry) : response;
}

/**
 * Typed client generated from the API's OpenAPI document (npm run api:generate).
 * The API is always same-origin under /api, on every band subdomain.
 */
export const api = createClient<paths>({
  baseUrl: new URL('/api', window.location.origin).href,
  fetch: sessionFetch,
});
