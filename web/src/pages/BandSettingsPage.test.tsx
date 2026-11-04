import { screen, within } from '@testing-library/react';
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
    http.get(apiUrl('/expense-categories'), () => HttpResponse.json(CATEGORIES)),
  );
  startSession('token');
}

const CATEGORIES = [
  { id: 'c-gear', name: 'Equipamento', defaultKey: 'GEAR', archived: false },
  { id: 'c-old', name: 'Flyers', color: '#00aa88', archived: true },
];

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

  it('lets an admin add, rename, archive and delete expense categories', async () => {
    api('ADMIN');
    const calls: { method: string; path: string; body: unknown }[] = [];
    const record = async ({ request }: { request: Request }) => {
      const url = new URL(request.url);
      const text = await request.text();
      calls.push({
        method: request.method,
        path: url.pathname,
        body: text ? JSON.parse(text) : null,
      });
      return HttpResponse.json(CATEGORIES[0]);
    };
    server.use(
      http.post(apiUrl('/expense-categories'), record),
      http.patch(apiUrl('/expense-categories/c-gear'), record),
      http.post(apiUrl('/expense-categories/c-gear/archive'), record),
      http.delete(apiUrl('/expense-categories/c-old'), () => problem(409, 'CATEGORY_IN_USE')),
    );
    const user = userEvent.setup();
    renderRoute('/settings', { host: BAND });

    const heading = await screen.findByRole('heading', { name: 'Categorias de gastos' });
    const card = heading.closest('section') as HTMLElement;
    // A default category shows its translation; archived ones say so.
    expect(await within(card).findByText('Equipamento')).toBeInTheDocument();
    expect(within(card).getByText('Arquivada')).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Reativar: Flyers' })).toBeInTheDocument();

    await user.click(within(card).getByRole('button', { name: 'Nova categoria' }));
    const add = within(card).getByRole('form', { name: 'Nova categoria' });
    await user.type(within(add).getByLabelText('Nome'), 'Shows');
    await user.click(within(add).getByRole('button', { name: 'Salvar' }));

    await user.click(await within(card).findByRole('button', { name: 'Editar: Equipamento' }));
    const edit = within(card).getByRole('form', { name: 'Editar Equipamento' });
    await user.click(within(edit).getByLabelText('Usar uma cor'));
    await user.click(within(edit).getByRole('button', { name: 'Salvar' }));

    await user.click(await within(card).findByRole('button', { name: 'Arquivar: Equipamento' }));
    await user.click(within(card).getByRole('button', { name: 'Excluir: Flyers' }));
    await user.click(within(card).getByRole('button', { name: 'Confirmar' }));

    expect(
      await within(card).findByText('Há gastos nesta categoria. Arquive em vez de excluir.'),
    ).toBeInTheDocument();
    expect(calls).toEqual([
      { method: 'POST', path: '/api/expense-categories', body: { name: 'Shows' } },
      // The name did not change, so the default keeps its translation: only the color is sent.
      { method: 'PATCH', path: '/api/expense-categories/c-gear', body: { color: '#ff6f61' } },
      { method: 'POST', path: '/api/expense-categories/c-gear/archive', body: null },
    ]);
  });
});
