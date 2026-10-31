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
