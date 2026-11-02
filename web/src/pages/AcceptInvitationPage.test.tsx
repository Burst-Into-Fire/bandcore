import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { startSession } from '../auth/session';
import { browser } from '../lib/navigation';
import { pendingInvitation } from '../lib/pendingInvitation';
import { renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

const PREVIEW = {
  bandName: 'The Burst',
  bandSlug: 'burst',
  email: 'bia@example.com',
  role: 'MEMBER',
};

function previewApi() {
  server.use(http.post(apiUrl('/invitations/preview'), () => HttpResponse.json(PREVIEW)));
}

afterEach(() => localStorage.clear());

describe('AcceptInvitationPage', () => {
  it('asks a visitor to log in or sign up with the invited email, and keeps the token', async () => {
    previewApi();
    const { router } = renderRoute('/invitations/accept#token=tok_123');

    expect(
      await screen.findByRole('heading', { name: 'Convite para The Burst' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('O convite é para bia@example.com, com o papel de Integrante.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Finvitations%2Faccept',
    );
    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveAttribute(
      'href',
      '/signup?email=bia%40example.com',
    );
    expect(router.state.location.hash).toBe('');
    expect(pendingInvitation()).toBe('tok_123');
  });

  it('accepts with the session and opens the band without another password', async () => {
    previewApi();
    const assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined);
    let accepted: unknown;
    let handoff: unknown;
    server.use(
      http.get(apiUrl('/me'), () =>
        HttpResponse.json({
          id: 'u',
          email: 'bia@example.com',
          displayName: 'Bia',
          emailVerified: true,
        }),
      ),
      http.post(apiUrl('/invitations/accept'), async ({ request }) => {
        accepted = await request.json();
        return HttpResponse.json({
          bandSlug: 'burst',
          bandUrl: 'http://burst.localhost:3000',
          role: 'MEMBER',
        });
      }),
      http.post(apiUrl('/auth/handoff'), async ({ request }) => {
        handoff = await request.json();
        return HttpResponse.json({ url: 'http://burst.localhost:3000/auth/handoff#code=c' });
      }),
    );
    startSession('central');
    const user = userEvent.setup();
    renderRoute('/invitations/accept#token=tok_123');

    expect(
      await screen.findByText('Você está conectado como bia@example.com.'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Aceitar convite' }));

    await vi.waitFor(() =>
      expect(assign).toHaveBeenCalledWith('http://burst.localhost:3000/auth/handoff#code=c'),
    );
    expect(accepted).toEqual({ token: 'tok_123' });
    expect(handoff).toEqual({ slug: 'burst' });
    expect(pendingInvitation()).toBeNull();
  });

  it('explains when the logged-in account is not the invited one', async () => {
    previewApi();
    server.use(
      http.get(apiUrl('/me'), () =>
        HttpResponse.json({
          id: 'u',
          email: 'carla@example.com',
          displayName: 'Carla',
          emailVerified: true,
        }),
      ),
      http.post(apiUrl('/invitations/accept'), () => problem(403, 'INVITATION_EMAIL_MISMATCH')),
    );
    startSession('central');
    const user = userEvent.setup();
    renderRoute('/invitations/accept#token=tok_123');

    await user.click(await screen.findByRole('button', { name: 'Aceitar convite' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este convite foi enviado para outro email.',
    );
    expect(screen.getByRole('button', { name: 'Entrar com outra conta' })).toBeInTheDocument();
  });

  it('says so when the invitation is invalid or expired', async () => {
    server.use(http.post(apiUrl('/invitations/preview'), () => problem(400, 'TOKEN_INVALID')));

    renderRoute('/invitations/accept#token=old');

    expect(await screen.findByRole('alert')).toHaveTextContent('Este link é inválido ou expirou.');
    expect(pendingInvitation()).toBeNull();
  });
});
