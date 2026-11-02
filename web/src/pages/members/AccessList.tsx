import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { textLink } from '../../components/buttons';
import { FormAlert } from '../../components/FormAlert';
import { errorMessage } from '../errorMessage';
import { roleLabel } from '../roles';
import { Confirm } from './Confirm';
import { ROLES, type Membership, type Role } from './types';

type Pending = { membership: Membership; role?: Role; remove?: boolean } | null;

/** Accounts with access (admins): change someone's role or remove their access, confirmed in the page. */
export function AccessList({
  memberships,
  currentUserId,
}: {
  memberships: readonly Membership[];
  currentUserId: string | undefined;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<Pending>(null);
  const change = useMutation({
    mutationFn: async (target: NonNullable<Pending>) => {
      const path = { params: { path: { membershipId: target.membership.id } } };
      return target.remove
        ? unwrap(await api.DELETE('/memberships/{membershipId}', { ...path, parseAs: 'text' }))
        : unwrap(
            await api.PATCH('/memberships/{membershipId}', {
              ...path,
              body: { role: target.role ?? target.membership.role },
              parseAs: 'text',
            }),
          );
    },
    onSuccess: async () => {
      setPending(null);
      await queryClient.invalidateQueries({ queryKey: ['memberships'] });
      await queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  return (
    <ul className="divide-border flex flex-col divide-y">
      {memberships.map((membership) => {
        const open = pending?.membership.id === membership.id;
        const you = membership.userId === currentUserId;
        return (
          <li key={membership.id} className="flex flex-col gap-3 py-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-medium">
                  {membership.displayName}
                  {you ? <span className="text-muted"> ({t('access.you')})</span> : null}
                </span>
                <span className="text-caption text-muted truncate">{membership.email}</span>
              </div>
              <div className="flex items-center gap-3">
                <label className="sr-only" htmlFor={`role-${membership.id}`}>
                  {t('access.changeRole', { name: membership.displayName })}
                </label>
                <select
                  id={`role-${membership.id}`}
                  value={open && pending.role ? pending.role : membership.role}
                  onChange={(event) => {
                    change.reset();
                    setPending({ membership, role: event.target.value as Role });
                  }}
                  className="border-border bg-surface-raised rounded-lg border px-2 py-1"
                >
                  {ROLES.map((role) => (
                    <option key={role} value={role}>
                      {roleLabel(t, role)}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className={textLink}
                  onClick={() => {
                    change.reset();
                    setPending({ membership, remove: true });
                  }}
                >
                  {t('access.remove')}
                </button>
              </div>
            </div>
            {open && pending.remove ? (
              <Confirm
                question={t('access.confirmRemove', { name: membership.displayName })}
                pending={change.isPending}
                onConfirm={() => change.mutate(pending)}
                onCancel={() => setPending(null)}
              />
            ) : null}
            {open && pending.role && pending.role !== membership.role ? (
              <Confirm
                question={t('access.confirmRole', {
                  name: membership.displayName,
                  role: roleLabel(t, pending.role),
                })}
                pending={change.isPending}
                onConfirm={() => change.mutate(pending)}
                onCancel={() => setPending(null)}
              />
            ) : null}
            {open && change.isError ? <FormAlert message={errorMessage(t, change.error)} /> : null}
          </li>
        );
      })}
    </ul>
  );
}
