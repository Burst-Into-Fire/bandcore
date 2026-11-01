import { useEffect, useSyncExternalStore } from 'react';

import { ensureSession, getSessionState, subscribeToSession, type SessionState } from './session';

/** The current session; restores it from the refresh cookie on first use. */
export function useSession(): SessionState {
  const session = useSyncExternalStore(subscribeToSession, getSessionState);
  useEffect(() => {
    ensureSession();
  }, []);
  return session;
}
