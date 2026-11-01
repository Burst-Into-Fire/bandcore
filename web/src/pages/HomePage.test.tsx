import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { renderRoute } from '../test/render';
import { apiUrl, server } from '../test/server';

describe('HomePage', () => {
  it('renders pt-BR texts and the API status from the mocked API', async () => {
    renderRoute('/');

    expect(screen.getByRole('heading', { name: 'Bem-vindo ao bandcore' })).toBeInTheDocument();
    expect(await screen.findByText('Online')).toBeInTheDocument();
  });

  it('offers login and sign up to anonymous visitors', async () => {
    renderRoute('/');

    expect(await screen.findByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/login');
    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveAttribute('href', '/signup');
  });

  it('links to the bands of a returning user', async () => {
    server.use(
      http.post(apiUrl('/auth/refresh'), () =>
        HttpResponse.json({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900 }),
      ),
    );

    renderRoute('/');

    expect(await screen.findByRole('link', { name: 'Minhas bandas' })).toHaveAttribute(
      'href',
      '/bands',
    );
  });

  it('shows the API as unavailable when the status check fails', async () => {
    server.use(http.get(apiUrl('/status'), () => new HttpResponse(null, { status: 503 })));

    renderRoute('/');

    expect(await screen.findByText('Indisponível')).toBeInTheDocument();
  });

  it('renders the not found page for unknown routes', () => {
    renderRoute('/does-not-exist');

    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voltar ao início' })).toHaveAttribute('href', '/');
  });
});
