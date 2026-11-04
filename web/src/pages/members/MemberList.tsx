import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { StatusBadge } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { SelectField } from '../../components/SelectField';
import { primaryButton, textLink } from '../../components/buttons';
import { errorMessage } from '../errorMessage';
import { MemberForm } from './MemberForm';
import type { Member, Membership } from './types';

type Pending = { memberId: string; action: 'edit' | 'deactivate' | 'delete' | 'link' } | null;

/** Action names (not UI text). */
const DEACTIVATE = 'deactivate';
const DELETE = 'delete';

/**
 * Members with name, instrument, linked account and status. Admins ({@code canManage}) also get
 * edit, deactivate or reactivate, delete and link or unlink, each confirmed in the page.
 */
export function MemberList({
  members,
  memberships,
  canManage,
}: {
  members: readonly Member[];
  memberships: readonly Membership[];
  canManage: boolean;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<Pending>(null);
  const [linkTo, setLinkTo] = useState('');
  const accounts = new Map(memberships.map((membership) => [membership.userId, membership]));
  const unlinkedAccounts = memberships.filter((membership) => !membership.memberId);
  const change = useMutation({
    mutationFn: async ({ member, action }: { member: Member; action: string }) => {
      const path = { params: { path: { memberId: member.id } } };
      switch (action) {
        case 'deactivate':
          return unwrap(await api.POST('/members/{memberId}/deactivate', path));
        case 'activate':
          return unwrap(await api.POST('/members/{memberId}/activate', path));
        case 'delete':
          return unwrap(await api.DELETE('/members/{memberId}', { ...path, parseAs: 'text' }));
        case 'unlink':
          return unwrap(await api.DELETE('/members/{memberId}/account', path));
        default:
          return unwrap(
            await api.PUT('/members/{memberId}/account', { ...path, body: { userId: linkTo } }),
          );
      }
    },
    onSuccess: async () => {
      setPending(null);
      await queryClient.invalidateQueries({ queryKey: ['members'] });
      await queryClient.invalidateQueries({ queryKey: ['memberships'] });
    },
  });

  /** Admins see whose account it is; everyone else only whether there is one. */
  const accountLabel = (member: Member, account: Membership | undefined) => {
    if (account) {
      return t('members.linkedTo', { name: account.displayName });
    }
    return member.linkedToAccount ? t('members.hasAccount') : t('members.noAccount');
  };

  const start = (memberId: string, action: NonNullable<Pending>['action']) => {
    change.reset();
    setLinkTo(unlinkedAccounts[0]?.userId ?? '');
    setPending({ memberId, action });
  };

  return (
    <ul className="divide-border flex flex-col divide-y">
      {members.map((member) => {
        const account = member.userId ? accounts.get(member.userId) : undefined;
        const open = pending?.memberId === member.id ? pending.action : null;
        return (
          <li key={member.id} className="flex flex-col gap-3 py-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-medium">{member.displayName}</span>
                <span className="text-caption text-muted">
                  {[member.instrument, accountLabel(member, account)].filter(Boolean).join(' · ')}
                </span>
              </div>
              <StatusBadge tone={member.status === 'ACTIVE' ? 'success' : 'neutral'}>
                {member.status === 'ACTIVE' ? t('members.active') : t('members.inactive')}
              </StatusBadge>
            </div>
            {canManage && !open ? (
              <div className="flex flex-wrap gap-x-4 gap-y-1">
                <button type="button" className={textLink} onClick={() => start(member.id, 'edit')}>
                  {t('members.edit')}
                </button>
                {member.status === 'ACTIVE' ? (
                  <button
                    type="button"
                    className={textLink}
                    onClick={() => start(member.id, 'deactivate')}
                  >
                    {t('members.deactivate')}
                  </button>
                ) : (
                  <button
                    type="button"
                    className={textLink}
                    onClick={() => change.mutate({ member, action: 'activate' })}
                  >
                    {t('members.activate')}
                  </button>
                )}
                {member.userId ? (
                  <button
                    type="button"
                    className={textLink}
                    onClick={() => change.mutate({ member, action: 'unlink' })}
                  >
                    {t('members.unlink')}
                  </button>
                ) : (
                  <button
                    type="button"
                    className={textLink}
                    onClick={() => start(member.id, 'link')}
                  >
                    {t('members.link')}
                  </button>
                )}
                <button
                  type="button"
                  className={textLink}
                  onClick={() => start(member.id, 'delete')}
                >
                  {t('members.delete')}
                </button>
              </div>
            ) : null}
            {open === 'edit' ? (
              <MemberForm member={member} onDone={() => setPending(null)} />
            ) : null}
            {open === 'deactivate' ? (
              <Confirm
                question={t('members.confirmDeactivate', { name: member.displayName })}
                pending={change.isPending}
                onConfirm={() => change.mutate({ member, action: DEACTIVATE })}
                onCancel={() => setPending(null)}
              />
            ) : null}
            {open === 'delete' ? (
              <Confirm
                question={t('members.confirmDelete', { name: member.displayName })}
                pending={change.isPending}
                onConfirm={() => change.mutate({ member, action: DELETE })}
                onCancel={() => setPending(null)}
              />
            ) : null}
            {open === 'link' ? (
              unlinkedAccounts.length === 0 ? (
                <p className="text-muted">{t('members.noAccountsToLink')}</p>
              ) : (
                <div className="bg-surface-raised flex flex-col gap-3 rounded-lg p-3 sm:flex-row sm:items-end">
                  <div className="flex-1">
                    <SelectField
                      label={t('members.chooseAccount')}
                      value={linkTo}
                      onChange={(event) => setLinkTo(event.target.value)}
                      options={unlinkedAccounts.map((membership) => ({
                        value: membership.userId,
                        label: `${membership.displayName} (${membership.email})`,
                      }))}
                    />
                  </div>
                  <button
                    type="button"
                    className={primaryButton}
                    disabled={change.isPending}
                    onClick={() => change.mutate({ member, action: 'link' })}
                  >
                    {t('members.link')}
                  </button>
                </div>
              )
            ) : null}
            {open && change.isError ? <FormAlert message={errorMessage(t, change.error)} /> : null}
            {!open && change.isError && change.variables.member.id === member.id ? (
              <FormAlert message={errorMessage(t, change.error)} />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
