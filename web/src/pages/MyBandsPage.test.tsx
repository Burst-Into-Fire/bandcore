import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { startSession } from '../auth/session';
import { browser } from '../lib/navigation';
import { renderRoute } from '../test/render';
import { apiUrl, server } from '../test/server';

describe('MyBandsPage', () => {
  it('opens a band with a one-time link instead of asking for the password again', async () => {
    const assign = vi.spyOn(browser, 'assign').mockImplementation(() => undefined);
    let body: unknown;
    server.use(
      http.get(apiUrl('/me/bands'), () =>
        HttpResponse.json([
          {
            id: 'b',
            slug: 'burst',
            name: 'The Burst',
            role: 'ADMIN',
            url: 'http://burst.localhost:3000',
          },
        ]),
      ),
      http.post(apiUrl('/auth/handoff'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ url: 'http://burst.localhost:3000/auth/handoff#code=xyz' });
      }),
    );
    startSession('central-token');
    const user = userEvent.setup();
    renderRoute('/bands');

    expect(await screen.findByText('The Burst')).toBeInTheDocument();
    expect(screen.getByText('Administrador')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Abrir' }));

    await vi.waitFor(() =>
      expect(assign).toHaveBeenCalledWith('http://burst.localhost:3000/auth/handoff#code=xyz'),
    );
    expect(body).toEqual({ slug: 'burst' });
  });
});
