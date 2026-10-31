import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

describe('VerifyEmailPage', () => {
  it('confirms the email with the token from the link and removes it from the address', async () => {
    let token: unknown;
    server.use(
      http.post(apiUrl('/auth/verify-email'), async ({ request }) => {
        token = ((await request.json()) as { token: string }).token;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { router } = renderRoute('/verify-email#token=abc_DEF-123');

    expect(
      await screen.findByText('Email confirmado! Agora você já pode entrar.'),
    ).toBeInTheDocument();
    expect(token).toBe('abc_DEF-123');
    expect(router.state.location.hash).toBe('');
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/login');
  });

  it('offers a new link when the token is invalid or expired', async () => {
    server.use(http.post(apiUrl('/auth/verify-email'), () => problem(400, 'TOKEN_INVALID')));

    renderRoute('/verify-email#token=used');

    expect(await screen.findByRole('alert')).toHaveTextContent('Este link é inválido ou expirou.');
    expect(screen.getByRole('heading', { name: 'Receber um novo link' })).toBeInTheDocument();
  });

  it('explains a link without a token', () => {
    renderRoute('/verify-email');

    expect(screen.getByRole('alert')).toHaveTextContent('O link de confirmação está incompleto.');
  });
});
