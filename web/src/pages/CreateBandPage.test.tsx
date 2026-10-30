import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { browser } from '../lib/navigation';
import { renderRoute } from '../test/render';
import { apiUrl, problem, server } from '../test/server';

describe('CreateBandPage', () => {
  beforeEach(() => {
    server.use(
      http.post(apiUrl('/auth/refresh'), () =>
        HttpResponse.json({ accessToken: 'central', tokenType: 'Bearer', expiresIn: 900 }),
      ),
      http.get(apiUrl('/bands/slug-availability'), ({ request }) => {
        const slug = new URL(request.url).searchParams.get('slug') ?? '';
        return HttpResponse.json(
          slug === 'taken-band'
            ? { slug, available: false, reason: 'SLUG_TAKEN' }
            : { slug, available: true },
        );
      }),
    );
  });

  it('suggests the address from the name, previews it and checks availability', async () => {
    renderRoute('/bands/new');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Nome da banda'), 'The Burst');

    expect(screen.getByLabelText('Endereço da banda')).toHaveValue('the-burst');
    expect(screen.getByText('Endereço: the-burst.localhost')).toBeInTheDocument();
    expect(await screen.findByText('Disponível')).toBeInTheDocument();
  });

  it('shows when an address is already taken', async () => {
    renderRoute('/bands/new');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Endereço da banda'), 'taken-band');

    expect(await screen.findByText(/Este endereço já está em uso/)).toBeInTheDocument();
  });

  it('rejects malformed addresses before asking the API', async () => {
    renderRoute('/bands/new');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Nome da banda'), 'Burst');
    const slug = screen.getByLabelText('Endereço da banda');
    await user.clear(slug);
    await user.type(slug, 'bad--slug');
    await user.click(screen.getByRole('button', { name: 'Criar banda' }));

    expect(await screen.findByText(/Use de 3 a 32 letras minúsculas/)).toBeInTheDocument();
  });

  it('creates the band and opens its login page', async () => {
    const assign = vi.spyOn(browser, 'assign').mockImplementation(() => {});
    server.use(
      http.post(apiUrl('/bands'), () =>
        HttpResponse.json(
          { id: 'b', slug: 'the-burst', name: 'The Burst', url: 'http://the-burst.localhost:3000' },
          { status: 201 },
        ),
      ),
    );
    renderRoute('/bands/new');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Nome da banda'), 'The Burst');
    await user.click(screen.getByRole('button', { name: 'Criar banda' }));

    await vi.waitFor(() =>
      expect(assign).toHaveBeenCalledWith('http://the-burst.localhost:3000/login'),
    );
  });

  it('shows why the API refused to create the band', async () => {
    server.use(http.post(apiUrl('/bands'), () => problem(403, 'EMAIL_NOT_VERIFIED')));
    renderRoute('/bands/new');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText('Nome da banda'), 'The Burst');
    await user.click(screen.getByRole('button', { name: 'Criar banda' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Confirme seu email antes de criar uma banda.',
    );
  });
});
