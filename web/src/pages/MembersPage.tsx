import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { Card, StatusBadge, ValueList } from '../components/Card';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { errorMessage } from './errorMessage';

/** Integrantes: who plays in the band. Management (M4-07) is added for admins. */
export function MembersPage() {
  const { t } = useTranslation();
  const members = useQuery({
    queryKey: ['members'],
    queryFn: async () => unwrap(await api.GET('/members')),
  });

  return (
    <>
      <PageHeader title={t('members.title')} subtitle={t('members.subtitle')} />
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
          <ValueList
            items={members.data.map((member) => ({
              key: member.id,
              label: member.displayName,
              detail: member.instrument ?? undefined,
              value: (
                <StatusBadge tone={member.status === 'ACTIVE' ? 'success' : 'neutral'}>
                  {member.status === 'ACTIVE' ? t('members.active') : t('members.inactive')}
                </StatusBadge>
              ),
            }))}
          />
        </Card>
      ) : null}
    </>
  );
}
