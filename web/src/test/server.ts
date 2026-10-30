import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/** Builds an absolute API URL, as the app's client does from window.location. */
export const apiUrl = (path: string) => new URL(`/api${path}`, window.location.origin).href;

/** Default handlers; override per test with server.use(...). */
export const handlers = [http.get(apiUrl('/status'), () => HttpResponse.json({ status: 'UP' }))];

export const server = setupServer(...handlers);
