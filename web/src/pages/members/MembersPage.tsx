import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { useBandAccess } from '../../auth/useBandAccess';
import { useLogout } from '../../auth/useLogout';
import { primaryButton, secondaryButton } from '../../components/buttons';
import { Card } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { PageHeader } from '../../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { errorMessage } from '../errorMessage';
import { AccessList } from './AccessList';
import { Invitations } from './Invitations';
import { MemberForm } from './MemberForm';
import { MemberList } from './MemberList';

/**
 * Integrantes: everyone sees the members; admins also manage them, who has access with which
 * role, and invitations. Anyone may leave the band.
 */
export function MembersPage() {
  const { t } = useTranslation();
  const { can, me } = useBandAccess();
  const canManage = can('member:manage');
  const [adding, setAdding] = useState(false);
  const members = useQuery({
    queryKey: ['members'],
    queryFn: async () => unwrap(await api.GET('/members')),
  });
  const memberships = useQuery({
    queryKey: ['memberships'],
    queryFn: async () => unwrap(await api.GET('/memberships')),
    enabled: canManage,
  });

  return (
    <>
      <PageHeader
        title={t('members.title')}
        subtitle={t('members.subtitle')}
        action={
          canManage && !adding ? (
            <button type="button" className={primaryButton} onClick={() => setAdding(true)}>
              {t('members.add')}
            </button>
          ) : null
        }
      />
      {adding ? <MemberForm onDone={() => setAdding(false)} /> : null}
      {members.isPending ? <LoadingState /> : null}
      {members.isError ? (
        <ErrorState
          message={errorMessage(t, members.error)}
          onRetry={() => void members.refetch()}
        />
      ) : null}
      {members.data?.length === 0 ? <EmptyState title={t('members.empty')} /> : null}
      {members.data && members.data.length > 0 ? (
        <Card>
          <MemberList
            members={members.data}
            memberships={memberships.data ?? []}
            canManage={canManage}
          />
        </Card>
      ) : null}
      {canManage && memberships.data ? (
        <Card title={t('access.title')}>
          <p className="text-muted -mt-2">{t('access.subtitle')}</p>
          <AccessList memberships={memberships.data} currentUserId={me.data?.id} />
        </Card>
      ) : null}
      {canManage && members.data ? <Invitations members={members.data} /> : null}
      <LeaveBand />
    </>
  );
}

function LeaveBand() {
  const { t } = useTranslation();
  const logout = useLogout();
  const [confirming, setConfirming] = useState(false);
  const leave = useMutation({
    mutationFn: async () => unwrap(await api.POST('/memberships/leave', { parseAs: 'text' })),
    onSuccess: () => void logout(),
  });

  return (
    <div className="flex flex-col items-start gap-2">
      {confirming ? (
        <Confirm
          question={t('members.confirmLeave')}
          pending={leave.isPending}
          onConfirm={() => leave.mutate()}
          onCancel={() => setConfirming(false)}
        />
      ) : (
        <button type="button" className={secondaryButton} onClick={() => setConfirming(true)}>
          {t('members.leave')}
        </button>
      )}
      {leave.isError ? <FormAlert message={errorMessage(t, leave.error)} /> : null}
    </div>
  );
}
