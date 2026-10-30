import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useNavigate } from 'react-router';

import { api } from '../api/client';
import { endSession } from './session';

/** Ends the session on this host: revokes the cookie, forgets the token and every cached query. */
export function useLogout(): () => Promise<void> {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useCallback(async () => {
    try {
      await api.POST('/auth/logout');
    } finally {
      endSession();
      queryClient.clear();
      void navigate('/login', { replace: true });
    }
  }, [navigate, queryClient]);
}
