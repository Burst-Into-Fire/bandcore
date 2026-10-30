import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

async function fillAndSubmit(name: string, email: string, password: string) {
  const user = userEvent.setup();
  if (name) await user.type(screen.getByLabelText('Seu nome'), name);
  if (email) await user.type(screen.getByLabelText('Email'), email);
  if (password) await user.type(screen.getByLabelText('Senha'), password);
  await user.click(screen.getByRole('button', { name: 'Criar conta' }));
}

describe('SignupPage', () => {
  it('validates the fields with the API rules before sending anything', async () => {
    let sent = false;
    server.use(
      http.post(apiUrl('/auth/signup'), () => {
        sent = true;
        return new HttpResponse(null, { status: 202 });
      }),
    );
    renderRoute('/signup');

    await fillAndSubmit('', 'not-an-email', 'short');

    expect(await screen.findByText('Preencha este campo.')).toBeInTheDocument();
    expect(screen.getByText('Informe um email válido.')).toBeInTheDocument();
    expect(screen.getByText('Use pelo menos 12 caracteres.')).toBeInTheDocument();
    expect(screen.getByLabelText('Senha')).toHaveAttribute('aria-invalid', 'true');
    expect(sent).toBe(false);
  });

  it('shows the API rejection of a common password on the password field', async () => {
    server.use(
      http.post(apiUrl('/auth/signup'), () =>
        problem(422, 'PASSWORD_TOO_COMMON', [
          { field: 'password', code: 'PASSWORD_TOO_COMMON', message: 'too common' },
        ]),
      ),
    );
    renderRoute('/signup');

    await fillAndSubmit('Ana', 'ana@example.com', 'qwerty123456');

    const message = await screen.findByText(/Essa senha é muito comum/);
    expect(screen.getByLabelText('Senha')).toHaveAccessibleDescription(
      expect.stringContaining(message.textContent ?? ''),
    );
  });

  it('asks the user to check the inbox after signing up', async () => {
    let body: unknown;
    server.use(
      http.post(apiUrl('/auth/signup'), async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 202, headers: { 'Content-Length': '0' } });
      }),
    );
    renderRoute('/signup');

    await fillAndSubmit('Ana', 'ana@example.com', 'a long and unique passphrase');

    expect(await screen.findByRole('heading', { name: 'Verifique seu email' })).toBeInTheDocument();
    expect(screen.getByText(/ana@example\.com/)).toBeInTheDocument();
    expect(body).toEqual({
      displayName: 'Ana',
      email: 'ana@example.com',
      password: 'a long and unique passphrase',
    });
  });
});
