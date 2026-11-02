import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { getSessionState } from '../auth/session';
import { BAND, renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

describe('HandoffPage', () => {
  it('exchanges the code for a session on the band host and opens the band', async () => {
    let code: unknown;
    server.use(
      http.post(apiUrl('/auth/handoff/exchange'), async ({ request }) => {
        code = ((await request.json()) as { code: string }).code;
        return HttpResponse.json({
          accessToken: 'band-token',
          tokenType: 'Bearer',
          expiresIn: 900,
        });
      }),
    );

    const { router } = renderRoute('/auth/handoff#code=abc_DEF-123', { host: BAND });

    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(code).toBe('abc_DEF-123');
    expect(router.state.location.hash).toBe('');
    expect(getSessionState().status).toBe('authenticated');
  });

  it('opens "my bands" on the central host', async () => {
    server.use(
      http.post(apiUrl('/auth/handoff/exchange'), () =>
        HttpResponse.json({ accessToken: 'central-token', tokenType: 'Bearer', expiresIn: 900 }),
      ),
      http.get(apiUrl('/me/bands'), () => HttpResponse.json([])),
    );

    const { router } = renderRoute('/auth/handoff#code=abc');

    await waitFor(() => expect(router.state.location.pathname).toBe('/bands'));
    expect(
      await screen.findByText('Você ainda não participa de nenhuma banda.'),
    ).toBeInTheDocument();
  });

  it('explains an expired or used link and offers the login page', async () => {
    server.use(http.post(apiUrl('/auth/handoff/exchange'), () => problem(401, 'UNAUTHENTICATED')));

    renderRoute('/auth/handoff#code=used', { host: BAND });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este link expirou ou já foi usado.',
    );
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/login');
  });
});
