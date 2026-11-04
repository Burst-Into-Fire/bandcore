import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { startSession } from '../auth/session';
import { browser } from '../lib/navigation';
import { emptyFinanceHandlers } from '../test/finance';
import { BAND, renderRoute } from '../test/render';
import { apiUrl, server } from '../test/server';
import { setViewportWidth } from '../test/viewport';

const ADMIN = ['band:read', 'band:manage', 'member:read', 'member:manage'];
const MEMBER = ['band:read', 'member:read'];

function bandApi(role: 'ADMIN' | 'MEMBER') {
  server.use(
    http.get(apiUrl('/me'), () =>
      HttpResponse.json({
        id: 'u',
        email: 'ana@example.com',
        displayName: 'Ana',
        emailVerified: true,
        band: { id: 'b', slug: 'burst', role, permissions: role === 'ADMIN' ? ADMIN : MEMBER },
      }),
    ),
    http.get(apiUrl('/band'), () =>
      HttpResponse.json({
        id: 'b',
        slug: 'burst',
        name: 'The Burst',
        currency: 'BRL',
        timezone: 'America/Sao_Paulo',
      }),
    ),
    http.get(apiUrl('/members'), () => HttpResponse.json([])),
    http.get(apiUrl('/products'), () => HttpResponse.json([])),
    ...emptyFinanceHandlers,
  );
  startSession('token');
}

function navigation() {
  return screen.getByRole('navigation', { name: 'Seções da banda' });
}

describe('AppShell', () => {
  it('shows the band and every section to an admin, with the current one marked', async () => {
    bandApi('ADMIN');
    renderRoute('/', { host: BAND });

    expect(await screen.findByText('The Burst')).toBeInTheDocument();
    const links = within(navigation()).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual([
      'Resumo',
      'Gastos',
      'Merch',
      'Integrantes',
      'Configurações',
    ]);
    expect(within(navigation()).getByRole('link', { name: 'Resumo' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(await screen.findByRole('heading', { name: /^Resumo/ })).toBeInTheDocument();
  });

  it('hides admin-only sections from members', async () => {
    bandApi('MEMBER');
    renderRoute('/', { host: BAND });

    await screen.findByText('The Burst');
    await vi.waitFor(() => expect(within(navigation()).getAllByRole('link')).toHaveLength(4));
    expect(
      within(navigation()).queryByRole('link', { name: 'Configurações' }),
    ).not.toBeInTheDocument();
  });

  it('navigates between sections', async () => {
    bandApi('ADMIN');
    const user = userEvent.setup();
    const { router } = renderRoute('/', { host: BAND });

    await user.click(await within(navigation()).findByRole('link', { name: 'Gastos' }));

    expect(router.state.location.pathname).toBe('/expenses');
    expect(screen.getByRole('heading', { name: 'Gastos' })).toBeInTheDocument();
    expect(await screen.findByText('Nenhum gasto registrado')).toBeInTheDocument();

    await user.click(within(navigation()).getByRole('link', { name: 'Merch' }));
    expect(router.state.location.pathname).toBe('/merch');
    expect(await screen.findByText('Nenhum produto ainda')).toBeInTheDocument();
  });

  it('uses a compact header and bottom navigation on a 360 px phone', async () => {
    setViewportWidth(360);
    bandApi('MEMBER');
    renderRoute('/', { host: BAND });

    await screen.findByText('The Burst');
    expect(screen.getAllByRole('banner')[0]).toHaveTextContent('The Burst');
    expect(screen.getAllByRole('navigation', { name: 'Seções da banda' })).toHaveLength(1);
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
    expect(
      within(navigation())
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Resumo', 'Gastos', 'Merch', 'Integrantes']);
  });

  it('switches band through the central host and logs out from the user menu', async () => {
    bandApi('ADMIN');
    const assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined);
    let body: unknown;
    server.use(
      http.post(apiUrl('/auth/handoff'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ url: 'http://app.localhost:3000/auth/handoff#code=xyz' });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/', { host: BAND });

    const menu = await screen.findByRole('button', { name: /Ana/ });
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    await user.click(menu);
    expect(menu).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('ana@example.com')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Trocar de banda' }));

    await vi.waitFor(() =>
      expect(assign).toHaveBeenCalledWith('http://app.localhost:3000/auth/handoff#code=xyz'),
    );
    expect(body).toEqual({});
  });

  it('closes the user menu with Escape and returns focus to its button', async () => {
    bandApi('ADMIN');
    const user = userEvent.setup();
    renderRoute('/', { host: BAND });

    const menu = await screen.findByRole('button', { name: /Ana/ });
    await user.click(menu);
    await user.keyboard('{Escape}');

    expect(menu).toHaveAttribute('aria-expanded', 'false');
    expect(menu).toHaveFocus();
  });

  it('offers a skip link to the content for keyboard users', async () => {
    bandApi('ADMIN');
    const user = userEvent.setup();
    renderRoute('/', { host: BAND });
    await screen.findByText('The Burst');

    await user.tab();

    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveFocus();
  });
});
