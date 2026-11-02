import { useMutation } from '@tanstack/react-query';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { browser } from '../lib/navigation';

/**
 * Opens another host with this session, without the password: a band by slug, or the central
 * host without one. The API answers with a one-time link valid for 60 seconds.
 */
export function useHandoff() {
  return useMutation({
    mutationFn: async (slug?: string) =>
      unwrap(await api.POST('/auth/handoff', { body: slug ? { slug } : {} })),
    onSuccess: (handoff) => browser.assign(handoff.url),
  });
}
