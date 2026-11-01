import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

describe('ForgotPasswordPage', () => {
  it('sends the email and shows the same confirmation whether or not the account exists', async () => {
    let body: unknown;
    server.use(
      http.post(apiUrl('/auth/password-reset'), async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/forgot-password');

    await user.type(screen.getByLabelText('Email'), 'ana@example.com');
    await user.click(screen.getByRole('button', { name: 'Enviar link' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Se existir uma conta com ana@example.com, enviamos um link para redefinir a senha.',
    );
    expect(body).toEqual({ email: 'ana@example.com' });
    expect(screen.getByRole('link', { name: 'Voltar para o login' })).toHaveAttribute(
      'href',
      '/login',
    );
  });

  it('validates the email before calling the API', async () => {
    let called = false;
    server.use(
      http.post(apiUrl('/auth/password-reset'), () => {
        called = true;
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/forgot-password');

    await user.click(screen.getByRole('button', { name: 'Enviar link' }));
    expect(await screen.findByText('Preencha este campo.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Email'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Enviar link' }));
    expect(await screen.findByText('Informe um email válido.')).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('explains when there were too many requests', async () => {
    server.use(http.post(apiUrl('/auth/password-reset'), () => problem(429, 'TOO_MANY_REQUESTS')));
    const user = userEvent.setup();
    renderRoute('/forgot-password');

    await user.type(screen.getByLabelText('Email'), 'ana@example.com');
    await user.click(screen.getByRole('button', { name: 'Enviar link' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Muitas tentativas. Aguarde um pouco e tente de novo.',
    );
  });
});
