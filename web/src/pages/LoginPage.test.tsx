import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { BAND, renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

const ME = {
  id: '0190c000-0000-7000-8000-000000000001',
  email: 'ana@example.com',
  displayName: 'Ana',
  emailVerified: true,
  band: { id: 'b', slug: 'burst', role: 'ADMIN', permissions: [] },
};

function bandApi() {
  server.use(
    http.get(apiUrl('/band/public-profile'), () =>
      HttpResponse.json({ slug: 'burst', name: 'The Burst' }),
    ),
    http.get(apiUrl('/me'), () => HttpResponse.json(ME)),
    http.get(apiUrl('/band'), () =>
      HttpResponse.json({
        id: 'b',
        slug: 'burst',
        name: 'The Burst',
        currency: 'BRL',
        timezone: 'America/Sao_Paulo',
      }),
    ),
  );
}

async function logIn(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Senha'), password);
  await user.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('LoginPage', () => {
  it('shows the band name and opens the band after login', async () => {
    bandApi();
    server.use(
      http.post(apiUrl('/auth/login'), () =>
        HttpResponse.json({ accessToken: 'band-token', tokenType: 'Bearer', expiresIn: 900 }),
      ),
    );
    renderRoute('/login', { host: BAND });

    expect(await screen.findByRole('heading', { name: 'Entrar em The Burst' })).toBeInTheDocument();
    await logIn('ana@example.com', 'a long and unique passphrase');

    expect(await screen.findByText('Olá, Ana!')).toBeInTheDocument();
    expect(screen.getByText('Seu papel nesta banda: Administrador')).toBeInTheDocument();
  });

  it('shows one generic message for any login failure and clears the password', async () => {
    bandApi();
    server.use(http.post(apiUrl('/auth/login'), () => problem(401, 'INVALID_CREDENTIALS')));
    renderRoute('/login', { host: BAND });

    await logIn('ana@example.com', 'wrong password');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Email ou senha incorretos, ou esta conta não tem acesso a esta banda.',
    );
    expect(screen.getByLabelText('Senha')).toHaveValue('');
  });

  it('says so when the band host does not exist', async () => {
    server.use(http.get(apiUrl('/band/public-profile'), () => problem(404, 'NOT_FOUND')));
    renderRoute('/login', { host: BAND });

    expect(
      await screen.findByRole('heading', { name: 'Esta banda não existe ou está indisponível.' }),
    ).toBeInTheDocument();
  });

  it('sends anonymous users to login and back to the page they asked for', async () => {
    server.use(
      http.post(apiUrl('/auth/login'), () =>
        HttpResponse.json({ accessToken: 'central', tokenType: 'Bearer', expiresIn: 900 }),
      ),
      http.get(apiUrl('/me/bands'), ({ request }) =>
        request.headers.get('Authorization') === 'Bearer central'
          ? HttpResponse.json([
              {
                id: 'b',
                slug: 'burst',
                name: 'The Burst',
                role: 'MEMBER',
                url: 'http://burst.localhost:3000',
              },
            ])
          : problem(401, 'UNAUTHENTICATED'),
      ),
    );
    const { router } = renderRoute('/bands');

    expect(await screen.findByRole('heading', { name: 'Entrar no bandcore' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?redirect=%2Fbands');

    await logIn('ana@example.com', 'a long and unique passphrase');

    expect(await screen.findByRole('heading', { name: 'Minhas bandas' })).toBeInTheDocument();
    expect(await screen.findByText('The Burst')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Abrir' })).toHaveAttribute(
      'href',
      'http://burst.localhost:3000/login',
    );
  });

  it('logout revokes the cookie, forgets the session and returns to login', async () => {
    bandApi();
    let loggedOut = false;
    server.use(
      http.post(apiUrl('/auth/refresh'), () =>
        HttpResponse.json({ accessToken: 'restored', tokenType: 'Bearer', expiresIn: 900 }),
      ),
      http.post(apiUrl('/auth/logout'), () => {
        loggedOut = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { router, queryClient } = renderRoute('/', { host: BAND });

    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }));

    expect(await screen.findByRole('heading', { name: 'Entrar em The Burst' })).toBeInTheDocument();
    expect(loggedOut).toBe(true);
    expect(router.state.location.pathname).toBe('/login');
    expect(queryClient.getQueryData(['me'])).toBeUndefined();
  });
});
