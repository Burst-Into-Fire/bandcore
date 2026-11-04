import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import type { z } from 'zod';

import { api } from '../../api/client';
import { applyProblem } from '../../api/forms';
import { unwrap } from '../../api/problem';
import { invitationSchema } from '../../auth/schemas';
import { primaryButton, textLink } from '../../components/buttons';
import { Card, StatusBadge } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { SelectField } from '../../components/SelectField';
import { ErrorState, LoadingState } from '../../components/states';
import { TextField } from '../../components/TextField';
import { translateKey } from '../../i18n/messages';
import { formatDate } from '../../lib/dates';
import { errorMessage } from '../errorMessage';
import { roleLabel } from '../roles';
import { ROLES, type Invitation, type Member } from './types';

type InviteInput = z.input<typeof invitationSchema>;
type InviteValues = z.output<typeof invitationSchema>;

/** Action name (not UI text). */
const REVOKE = 'revoke';

const STATUS_TONE = {
  PENDING: 'warning',
  ACCEPTED: 'success',
  EXPIRED: 'neutral',
  REVOKED: 'neutral',
} as const;

/** Invite by email with a role (and optionally the member to link); resend and revoke (admins). */
export function Invitations({ members }: { members: readonly Member[] }) {
  const { t } = useTranslation();
  const invitations = useQuery({
    queryKey: ['invitations'],
    queryFn: async () => unwrap(await api.GET('/invitations')),
  });
  const unlinked = members.filter((member) => !member.userId);

  return (
    <Card title={t('invitations.title')}>
      <p className="text-muted -mt-2">{t('invitations.subtitle')}</p>
      <InviteForm members={unlinked} />
      {invitations.isPending ? <LoadingState /> : null}
      {invitations.isError ? (
        <ErrorState
          message={errorMessage(t, invitations.error)}
          onRetry={() => void invitations.refetch()}
        />
      ) : null}
      {invitations.data?.length === 0 ? (
        <p className="text-muted">{t('invitations.empty')}</p>
      ) : null}
      {invitations.data && invitations.data.length > 0 ? (
        <InvitationList invitations={invitations.data} />
      ) : null}
    </Card>
  );
}

function InviteForm({ members }: { members: readonly Member[] }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const form = useForm<InviteInput, unknown, InviteValues>({
    resolver: zodResolver(invitationSchema),
    defaultValues: { email: '', role: 'MEMBER', memberId: '' },
  });
  const invite = useMutation({
    mutationFn: async (values: InviteValues) =>
      unwrap(
        await api.POST('/invitations', {
          body: {
            email: values.email,
            role: values.role,
            ...(values.memberId ? { memberId: values.memberId } : {}),
          },
        }),
      ),
    onSuccess: async (invitation) => {
      setSentTo(invitation.email);
      form.reset();
      await queryClient.invalidateQueries({ queryKey: ['invitations'] });
    },
    onError: (error) => {
      setSentTo(null);
      applyProblem(error, form.setError, ['email', 'role', 'memberId']);
    },
  });
  const { errors } = form.formState;

  return (
    <form
      noValidate
      aria-label={t('invitations.title')}
      className="bg-surface-raised flex flex-col gap-3 rounded-lg p-3"
      onSubmit={form.handleSubmit((values) => invite.mutate(values))}
    >
      <FormAlert message={translateKey(t, errors.root?.server?.message)} />
      <div className="grid gap-3 md:grid-cols-3">
        <TextField
          label={t('fields.email')}
          type="email"
          autoComplete="off"
          inputMode="email"
          error={translateKey(t, errors.email?.message)}
          {...form.register('email')}
        />
        <SelectField
          label={t('fields.role')}
          options={ROLES.map((role) => ({ value: role, label: roleLabel(t, role) }))}
          error={translateKey(t, errors.role?.message)}
          {...form.register('role')}
        />
        <SelectField
          label={t('fields.linkedMember')}
          options={[
            { value: '', label: t('invitations.noMember') },
            ...members.map((member) => ({ value: member.id, label: member.displayName })),
          ]}
          error={translateKey(t, errors.memberId?.message)}
          {...form.register('memberId')}
        />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" className={primaryButton} disabled={invite.isPending}>
          {invite.isPending ? t('invitations.inviting') : t('invitations.invite')}
        </button>
        {sentTo ? (
          <p role="status" className="text-muted">
            {t('invitations.sent', { email: sentTo })}
          </p>
        ) : null}
      </div>
    </form>
  );
}

function InvitationList({ invitations }: { invitations: readonly Invitation[] }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [revoking, setRevoking] = useState<string | null>(null);
  const change = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'resend' | 'revoke' }) => {
      const path = { params: { path: { invitationId: id } } };
      return action === 'resend'
        ? unwrap(await api.POST('/invitations/{invitationId}/resend', path))
        : unwrap(await api.DELETE('/invitations/{invitationId}', { ...path, parseAs: 'text' }));
    },
    onSuccess: async () => {
      setRevoking(null);
      await queryClient.invalidateQueries({ queryKey: ['invitations'] });
    },
  });

  return (
    <ul className="divide-border flex flex-col divide-y">
      {invitations.map((invitation) => {
        const open = invitation.status === 'PENDING' || invitation.status === 'EXPIRED';
        return (
          <li key={invitation.id} className="flex flex-col gap-2 py-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-medium">{invitation.email}</span>
                <span className="text-caption text-muted">
                  {roleLabel(t, invitation.role)}
                  {invitation.status === 'PENDING'
                    ? ` · ${t('invitations.expires', { date: formatDate(invitation.expiresAt) })}`
                    : ''}
                </span>
              </div>
              <StatusBadge tone={STATUS_TONE[invitation.status]}>
                {t(`invitations.status.${invitation.status}`)}
              </StatusBadge>
            </div>
            {open && revoking !== invitation.id ? (
              <div className="flex flex-wrap gap-x-4">
                <button
                  type="button"
                  className={textLink}
                  disabled={change.isPending}
                  onClick={() => change.mutate({ id: invitation.id, action: 'resend' })}
                >
                  {t('invitations.resend')}
                </button>
                {invitation.status === 'PENDING' ? (
                  <button
                    type="button"
                    className={textLink}
                    onClick={() => setRevoking(invitation.id)}
                  >
                    {t('invitations.revoke')}
                  </button>
                ) : null}
              </div>
            ) : null}
            {revoking === invitation.id ? (
              <Confirm
                question={t('invitations.confirmRevoke', { email: invitation.email })}
                pending={change.isPending}
                onConfirm={() => change.mutate({ id: invitation.id, action: REVOKE })}
                onCancel={() => setRevoking(null)}
              />
            ) : null}
            {change.isError && change.variables.id === invitation.id ? (
              <FormAlert message={errorMessage(t, change.error)} />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
