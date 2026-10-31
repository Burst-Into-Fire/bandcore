import createClient from 'openapi-fetch';

import type { paths } from './schema';

/**
 * Typed client generated from the API's OpenAPI document (npm run api:generate).
 * The API is always same-origin under /api, on every band subdomain.
 */
export const api = createClient<paths>({
  baseUrl: new URL('/api', window.location.origin).href,
  // Resolve fetch on every call, so test interceptors installed later still apply.
  fetch: (request) => globalThis.fetch(request),
});
