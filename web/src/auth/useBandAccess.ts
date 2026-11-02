import { useQuery } from '@tanstack/react-query';

import { api } from '../api/client';
import { unwrap } from '../api/problem';

/** Permission names, as in the API (docs/security/permissions.md). */
export type Permission =
  | 'band:read'
  | 'band:manage'
  | 'member:read'
  | 'member:manage'
  | 'finance:read'
  | 'finance:write'
  | 'finance:manage'
  | 'merch:read'
  | 'sale:write'
  | 'sale:refund'
  | 'merch:manage'
  | 'event:close'
  | 'audit:read';

/**
 * The logged-in user on this band host and what they may do. The UI only hides what the user
 * cannot do; the API enforces every permission anyway.
 */
export function useBandAccess() {
  const me = useQuery({ queryKey: ['me'], queryFn: async () => unwrap(await api.GET('/me')) });
  const permissions = new Set<string>(me.data?.band?.permissions ?? []);
  return {
    me,
    role: me.data?.band?.role,
    can: (permission: Permission) => permissions.has(permission),
  };
}
