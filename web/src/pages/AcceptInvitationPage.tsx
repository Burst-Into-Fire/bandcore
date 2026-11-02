import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';

import { api } from '../api/client';
import { ApiError, unwrap } from '../api/problem';
import { useHandoff } from '../auth/useHandoff';
import { useLogout } from '../auth/useLogout';
import { useSession } from '../auth/useSession';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton, secondaryButton } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { LoadingState } from '../components/states';
import { forgetInvitation, pendingInvitation, rememberInvitation } from '../lib/pendingInvitation';
import { errorMessage } from './errorMessage';
import { roleLabel } from './roles';

const HERE = '/invitations/accept';

/**
 * Landing page of an invitation link (`/invitations/accept#token=...`) on the central host. The
 * token is kept while the invitee logs in or signs up (and verifies the email), then the
 * invitation is accepted and the band opens through a handoff, without another password.
 */
export function AcceptInvitationPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const session = useSession();
  const [token] = useState(() => {
    const fromLink = new URLSearchParams(location.hash.slice(1)).get('token');
    if (fromLink) {
      rememberInvitation(fromLink);
      return fromLink;
    }
    return pendingInvitation();
  });

  useEffect(() => {
    if (location.hash) {
      void navigate(location.pathname, { replace: true });
    }
  }, [location.hash, location.pathname, navigate]);

  const preview = useQuery({
    queryKey: ['invitation-preview', token],
    queryFn: async () =>
      unwrap(await api.POST('/invitations/preview', { body: { token: token ?? '' } })),
    enabled: token !== null,
    retry: false,
  });

  if (!token || preview.isError) {
    if (preview.isError) {
      forgetInvitation();
    }
    return (
      <AuthLayout title={t('acceptInvitation.invalidTitle')}>
        <FormAlert
          message={preview.isError ? errorMessage(t, preview.error) : t('acceptInvitation.missing')}
        />
      </AuthLayout>
    );
  }
  if (preview.isPending || session.status === 'unknown') {
    return (
      <AuthLayout title={t('acceptInvitation.invalidTitle')}>
        <LoadingState label={t('acceptInvitation.loading')} />
      </AuthLayout>
    );
  }

  const invitation = preview.data;
  return (
    <AuthLayout title={t('acceptInvitation.title', { band: invitation.bandName })}>
      <p>
        {t('acceptInvitation.details', {
          email: invitation.email,
          role: roleLabel(t, invitation.role),
        })}
      </p>
      {session.status === 'anonymous' ? (
        <>
          <p className="text-muted">{t('acceptInvitation.loginOrSignUp')}</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link
              to={`/login?redirect=${encodeURIComponent(HERE)}`}
              className={`${primaryButton} text-center`}
            >
              {t('acceptInvitation.login')}
            </Link>
            <Link
              to={`/signup?email=${encodeURIComponent(invitation.email)}`}
              className={`${secondaryButton} text-center`}
            >
              {t('acceptInvitation.signUp')}
            </Link>
          </div>
        </>
      ) : (
        <Accept token={token} bandSlug={invitation.bandSlug} />
      )}
    </AuthLayout>
  );
}

function Accept({ token, bandSlug }: { token: string; bandSlug: string }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const logout = useLogout();
  const handoff = useHandoff();
  const me = useQuery({ queryKey: ['me'], queryFn: async () => unwrap(await api.GET('/me')) });
  const accept = useMutation({
    mutationFn: async () => unwrap(await api.POST('/invitations/accept', { body: { token } })),
    onSuccess: () => {
      forgetInvitation();
      handoff.mutate(bandSlug);
    },
  });
  const wrongAccount =
    accept.error instanceof ApiError && accept.error.code === 'INVITATION_EMAIL_MISMATCH';

  return (
    <>
      {me.data ? (
        <p className="text-muted">{t('acceptInvitation.loggedInAs', { email: me.data.email })}</p>
      ) : null}
      <FormAlert message={accept.isError ? errorMessage(t, accept.error) : undefined} />
      <FormAlert message={handoff.isError ? errorMessage(t, handoff.error) : undefined} />
      <button
        type="button"
        className={primaryButton}
        disabled={accept.isPending || accept.isSuccess}
        onClick={() => accept.mutate()}
      >
        {accept.isPending || accept.isSuccess
          ? t('acceptInvitation.accepting')
          : t('acceptInvitation.accept')}
      </button>
      {wrongAccount ? (
        <button
          type="button"
          className={secondaryButton}
          onClick={() =>
            void logout().then(() => navigate(`/login?redirect=${encodeURIComponent(HERE)}`))
          }
        >
          {t('acceptInvitation.switchAccount')}
        </button>
      ) : null}
    </>
  );
}
