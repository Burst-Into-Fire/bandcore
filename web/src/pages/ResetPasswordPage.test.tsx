import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

const NEW_PASSWORD = 'a brand new passphrase';

async function submit(password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Nova senha'), password);
  await user.click(screen.getByRole('button', { name: 'Salvar nova senha' }));
}

describe('ResetPasswordPage', () => {
  it('sets the new password with the token from the link and removes it from the address', async () => {
    let body: unknown;
    server.use(
      http.post(apiUrl('/auth/password-reset/confirm'), async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { router } = renderRoute('/reset-password#token=abc_DEF-123');
    expect(router.state.location.hash).toBe('');

    await submit(NEW_PASSWORD);

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Senha alterada! Entre com a nova senha.',
    );
    expect(body).toEqual({ token: 'abc_DEF-123', password: NEW_PASSWORD });
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/login');
  });

  it('checks the password length before calling the API', async () => {
    let called = false;
    server.use(
      http.post(apiUrl('/auth/password-reset/confirm'), () => {
        called = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderRoute('/reset-password#token=abc');

    await submit('short');

    expect(await screen.findByText('Use pelo menos 12 caracteres.')).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('shows the API password rules next to the field', async () => {
    server.use(
      http.post(apiUrl('/auth/password-reset/confirm'), () =>
        problem(422, 'PASSWORD_TOO_COMMON', [
          { field: 'password', code: 'PASSWORD_TOO_COMMON', message: 'Too common' },
        ]),
      ),
    );
    renderRoute('/reset-password#token=abc');

    await submit('password1234');

    expect(
      await screen.findByText('Essa senha é muito comum. Escolha outra, por exemplo uma frase.'),
    ).toBeInTheDocument();
  });

  it('offers a new link when the token is invalid or expired', async () => {
    server.use(
      http.post(apiUrl('/auth/password-reset/confirm'), () => problem(400, 'TOKEN_INVALID')),
    );
    renderRoute('/reset-password#token=used');

    await submit(NEW_PASSWORD);

    expect(await screen.findByRole('alert')).toHaveTextContent('Este link é inválido ou expirou.');
    expect(screen.getByRole('link', { name: 'Pedir um novo link' })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
  });

  it('explains a link without a token', () => {
    renderRoute('/reset-password');

    expect(screen.getByRole('alert')).toHaveTextContent('O link de redefinição está incompleto.');
    expect(screen.getByRole('link', { name: 'Pedir um novo link' })).toBeInTheDocument();
  });
});
