/**
 * The session lives in memory only: the access token is never written to localStorage or
 * sessionStorage, so an XSS on one page cannot read a long-lived credential. The refresh token is
 * an HttpOnly cookie the browser sends to /api/auth/refresh; on load (and on any 401) the app
 * calls refresh to get a new access token. Concurrent callers share one refresh request.
 */

export type SessionStatus = 'unknown' | 'authenticated' | 'anonymous';

export interface SessionState {
  status: SessionStatus;
  accessToken: string | null;
}

export const CSRF_HEADER = 'X-Requested-With';
export const CSRF_VALUE = 'XMLHttpRequest';

const INITIAL: SessionState = { status: 'unknown', accessToken: null };

let state: SessionState = INITIAL;
let refreshInFlight: Promise<boolean> | null = null;
const listeners = new Set<() => void>();

function setState(next: SessionState): void {
  state = next;
  listeners.forEach((listener) => listener());
}

export function getSessionState(): SessionState {
  return state;
}

export function subscribeToSession(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getAccessToken(): string | null {
  return state.accessToken;
}

export function startSession(accessToken: string): void {
  setState({ status: 'authenticated', accessToken });
}

export function endSession(): void {
  setState({ status: 'anonymous', accessToken: null });
}

/** Restores the session from the refresh cookie once, when the app starts. */
export function ensureSession(): void {
  if (state.status === 'unknown') {
    void refreshSession();
  }
}

/** Gets a new access token; resolves to false (and ends the session) when the cookie is gone. */
export function refreshSession(): Promise<boolean> {
  refreshInFlight ??= requestRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

async function requestRefresh(): Promise<boolean> {
  try {
    // Not through the API client: a 401 here must never trigger another refresh.
    const response = await globalThis.fetch(new URL('/api/auth/refresh', window.location.origin), {
      method: 'POST',
      credentials: 'same-origin',
      headers: { [CSRF_HEADER]: CSRF_VALUE },
    });
    if (!response.ok) {
      endSession();
      return false;
    }
    const body = (await response.json()) as { accessToken: string };
    startSession(body.accessToken);
    return true;
  } catch {
    endSession();
    return false;
  }
}

/** Tests only: back to the state of a fresh page load. */
export function resetSessionState(): void {
  state = INITIAL;
  refreshInFlight = null;
  listeners.clear();
}
