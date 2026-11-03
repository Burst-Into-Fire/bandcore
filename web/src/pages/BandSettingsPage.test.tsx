import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { startSession } from '../auth/session';
import { BAND, renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

const BAND_DATA = {
  id: 'b',
  slug: 'burst',
  name: 'The Burst',
  currency: 'BRL',
  timezone: 'America/Sao_Paulo',
};

function me(role: 'ADMIN' | 'MEMBER', permissions: string[]) {
  return {
    id: 'u',
    email: 'ana@example.com',
    displayName: 'Ana',
    emailVerified: true,
    band: { id: 'b', slug: 'burst', role, permissions },
  };
}

function api(role: 'ADMIN' | 'MEMBER') {
  const permissions = role === 'ADMIN' ? ['band:read', 'band:manage'] : ['band:read'];
  server.use(
    http.get(apiUrl('/me'), () => HttpResponse.json(me(role, permissions))),
    http.get(apiUrl('/band'), () => HttpResponse.json(BAND_DATA)),
  );
  startSession('token');
}

describe('BandSettingsPage', () => {
  it('lets an admin change the name, time zone and currency', async () => {
    api('ADMIN');
    let body: unknown;
    server.use(
      http.patch(apiUrl('/band'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({
          ...BAND_DATA,
          name: 'The Burst Live',
          timezone: 'Europe/Lisbon',
          currency: 'USD',
        });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/settings', { host: BAND });

    const name = await screen.findByLabelText('Nome da banda');
    expect(screen.getByLabelText('Moeda')).toHaveValue('BRL');
    expect(screen.getByText('burst.localhost')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Salvar alterações' })).toBeDisabled();
    await user.clear(name);
    await user.type(name, 'The Burst Live');
    await user.selectOptions(screen.getByLabelText('Fuso horário'), 'Europe/Lisbon');
    await user.selectOptions(screen.getByLabelText('Moeda'), 'Dólar americano (USD)');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));

    expect(await screen.findByText('Alterações salvas.')).toBeInTheDocument();
    expect(body).toEqual({ name: 'The Burst Live', timezone: 'Europe/Lisbon', currency: 'USD' });
    expect(screen.getByLabelText('Moeda')).toHaveValue('USD');
  });

  it('validates the name and shows API errors next to the field', async () => {
    api('ADMIN');
    server.use(
      http.patch(apiUrl('/band'), () =>
        problem(422, 'TIMEZONE_INVALID', [
          { field: 'timezone', code: 'TIMEZONE_INVALID', message: 'invalid' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderRoute('/settings', { host: BAND });

    const name = await screen.findByLabelText('Nome da banda');
    await user.clear(name);
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    expect(await screen.findByText('Preencha este campo.')).toBeInTheDocument();

    await user.type(name, 'Renamed');
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }));
    expect(await screen.findByText('Escolha um fuso horário da lista.')).toBeInTheDocument();
  });

  it('tells members that only admins change settings', async () => {
    api('MEMBER');
    renderRoute('/settings', { host: BAND });

    expect(
      await screen.findByText('Somente administradores podem alterar as configurações da banda.'),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Nome da banda')).not.toBeInTheDocument();
  });
});
