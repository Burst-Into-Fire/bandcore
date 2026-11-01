import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from '../api/client';
import { apiUrl, problem, server } from '../test/server';
import { getSessionState, refreshSession, startSession } from './session';

const tokens = (accessToken: string) =>
  HttpResponse.json({ accessToken, tokenType: 'Bearer', expiresIn: 900 });

/** Answers 200 only to the expected access token, like the API. */
const requiresToken = (token: string) => (request: Request) =>
  request.headers.get('Authorization') === `Bearer ${token}`
    ? HttpResponse.json({ ok: true })
    : problem(401, 'UNAUTHENTICATED');

describe('session', () => {
  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('shares one refresh between parallel requests that get 401 and retries each once', async () => {
    let refreshes = 0;
    server.use(
      http.post(apiUrl('/auth/refresh'), () => {
        refreshes++;
        return tokens('fresh');
      }),
      http.get(apiUrl('/me'), ({ request }) => requiresToken('fresh')(request)),
      http.get(apiUrl('/band'), ({ request }) => requiresToken('fresh')(request)),
      http.get(apiUrl('/members'), ({ request }) => requiresToken('fresh')(request)),
    );
    startSession('expired');

    const results = await Promise.all([api.GET('/me'), api.GET('/band'), api.GET('/members')]);

    expect(refreshes).toBe(1);
    expect(results.map((result) => result.response.status)).toEqual([200, 200, 200]);
    expect(getSessionState()).toEqual({ status: 'authenticated', accessToken: 'fresh' });
  });

  it('ends the session when refresh fails and returns the original 401', async () => {
    server.use(http.get(apiUrl('/me'), () => problem(401, 'UNAUTHENTICATED')));
    startSession('expired');

    const result = await api.GET('/me');

    expect(result.response.status).toBe(401);
    expect(getSessionState()).toEqual({ status: 'anonymous', accessToken: null });
  });

  it('never refreshes on a 401 from a sessionless endpoint', async () => {
    let refreshes = 0;
    server.use(
      http.post(apiUrl('/auth/refresh'), () => {
        refreshes++;
        return tokens('fresh');
      }),
      http.post(apiUrl('/auth/login'), () => problem(401, 'INVALID_CREDENTIALS')),
    );

    const result = await api.POST('/auth/login', { body: { email: 'a@b.c', password: 'x' } });

    expect(result.response.status).toBe(401);
    expect(refreshes).toBe(0);
  });

  it('sends the CSRF header on every request and the token only where it belongs', async () => {
    const seen: Record<string, Headers> = {};
    server.use(
      http.post(apiUrl('/auth/login'), ({ request }) => {
        seen.login = request.headers;
        return tokens('t1');
      }),
      http.get(apiUrl('/me'), ({ request }) => {
        seen.me = request.headers;
        return HttpResponse.json({});
      }),
    );
    startSession('t0');

    await api.POST('/auth/login', { body: { email: 'a@b.c', password: 'x' } });
    await api.GET('/me');

    expect(seen.login?.get('X-Requested-With')).toBe('XMLHttpRequest');
    expect(seen.login?.get('Authorization')).toBeNull();
    expect(seen.me?.get('Authorization')).toBe('Bearer t0');
    expect(seen.me?.get('X-Requested-With')).toBe('XMLHttpRequest');
  });

  it('never writes the access token to localStorage or sessionStorage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    server.use(http.post(apiUrl('/auth/refresh'), () => tokens('secret-token')));

    await refreshSession();
    startSession('another-secret');

    expect(setItem).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('secret');
  });
});
