import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import { startSession } from '../../auth/session';
import { BAND, renderRoute } from '../../test/render';
import { apiUrl, problem, server } from '../../test/server';

const ADMIN = ['band:read', 'band:manage', 'member:read', 'member:manage'];

const MEMBERS = [
  {
    id: 'm-ana',
    displayName: 'Ana',
    instrument: 'Vocal',
    linkedToAccount: true,
    userId: 'u-ana',
    status: 'ACTIVE',
    joinedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'm-bia',
    displayName: 'Bia',
    instrument: null,
    linkedToAccount: false,
    status: 'INACTIVE',
    joinedAt: '2026-01-01T00:00:00Z',
  },
];

const MEMBERSHIPS = [
  {
    id: 'ms-ana',
    userId: 'u-ana',
    displayName: 'Ana',
    email: 'ana@example.com',
    role: 'ADMIN',
    memberId: 'm-ana',
  },
  {
    id: 'ms-caio',
    userId: 'u-caio',
    displayName: 'Caio',
    email: 'caio@example.com',
    role: 'MEMBER',
  },
];

function bandApi(permissions: string[]) {
  server.use(
    http.get(apiUrl('/me'), () =>
      HttpResponse.json({
        id: 'u-ana',
        email: 'ana@example.com',
        displayName: 'Ana',
        emailVerified: true,
        band: {
          id: 'b',
          slug: 'burst',
          role: permissions === ADMIN ? 'ADMIN' : 'MEMBER',
          permissions,
        },
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
    http.get(apiUrl('/members'), () => HttpResponse.json(MEMBERS)),
    http.get(apiUrl('/memberships'), () => HttpResponse.json(MEMBERSHIPS)),
    http.get(apiUrl('/invitations'), () =>
      HttpResponse.json([
        {
          id: 'i1',
          email: 'dani@example.com',
          role: 'MEMBER',
          status: 'PENDING',
          expiresAt: '2026-01-22T15:00:00Z',
          createdAt: '2026-01-15T15:00:00Z',
        },
      ]),
    ),
  );
  startSession('token');
}

describe('MembersPage', () => {
  it('shows members read-only to non-admins, without access or invitations', async () => {
    bandApi(['band:read', 'member:read']);
    renderRoute('/members', { host: BAND });

    expect(await screen.findByText('Bia')).toBeInTheDocument();
    expect(screen.getByText('Vocal · Tem conta')).toBeInTheDocument();
    expect(screen.getByText('Inativo')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Novo integrante' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Acessos' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Convites' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sair da banda' })).toBeInTheDocument();
  });

  it('lets an admin add a member', async () => {
    bandApi(ADMIN);
    let body: unknown;
    server.use(
      http.post(apiUrl('/members'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...MEMBERS[1], id: 'new' }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/members', { host: BAND });

    await user.click(await screen.findByRole('button', { name: 'Novo integrante' }));
    const form = screen.getByRole('form', { name: 'Novo integrante' });
    await user.type(within(form).getByLabelText('Nome'), 'Duda');
    await user.type(within(form).getByLabelText('Instrumento ou função'), 'Bateria');
    await user.click(within(form).getByRole('button', { name: 'Salvar' }));

    await vi.waitFor(() => expect(body).toEqual({ displayName: 'Duda', instrument: 'Bateria' }));
  });

  it('explains why a member with history cannot be deleted', async () => {
    bandApi(ADMIN);
    server.use(http.delete(apiUrl('/members/:memberId'), () => problem(409, 'MEMBER_HAS_HISTORY')));
    const user = userEvent.setup();
    renderRoute('/members', { host: BAND });

    const bia = (await screen.findByText('Inativo')).closest('li')!;
    await user.click(within(bia).getByRole('button', { name: 'Excluir' }));
    await user.click(within(bia).getByRole('button', { name: 'Confirmar' }));

    expect(await within(bia).findByRole('alert')).toHaveTextContent('Desative em vez de excluir.');
  });

  it('changes a role only after confirmation and shows the last-admin rule', async () => {
    bandApi(ADMIN);
    let changed: unknown;
    server.use(
      http.patch(apiUrl('/memberships/:membershipId'), async ({ request, params }) => {
        if (params.membershipId === 'ms-ana') {
          return problem(409, 'LAST_ADMIN');
        }
        changed = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/members', { host: BAND });

    await user.selectOptions(await screen.findByLabelText('Mudar papel de Caio'), 'READ_ONLY');
    expect(changed).toBeUndefined();
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));
    await vi.waitFor(() => expect(changed).toEqual({ role: 'READ_ONLY' }));

    await user.selectOptions(screen.getByLabelText('Mudar papel de Ana'), 'MEMBER');
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('pelo menos um administrador');
  });

  it('invites by email with a role and lists pending invitations', async () => {
    bandApi(ADMIN);
    let body: unknown;
    server.use(
      http.post(apiUrl('/invitations'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          {
            id: 'i2',
            email: 'edu@example.com',
            role: 'READ_ONLY',
            status: 'PENDING',
            expiresAt: '2026-01-22T15:00:00Z',
            createdAt: '2026-01-15T15:00:00Z',
          },
          { status: 201 },
        );
      }),
    );
    const user = userEvent.setup();
    renderRoute('/members', { host: BAND });

    expect(await screen.findByText('dani@example.com')).toBeInTheDocument();
    expect(screen.getByText('Pendente')).toBeInTheDocument();
    const form = screen.getByRole('form', { name: 'Convites' });
    await user.type(within(form).getByLabelText('Email'), 'edu@example.com');
    await user.selectOptions(within(form).getByLabelText('Papel'), 'READ_ONLY');
    await user.selectOptions(
      within(form).getByLabelText('Vincular a um integrante (opcional)'),
      'm-bia',
    );
    await user.click(within(form).getByRole('button', { name: 'Convidar' }));

    expect(await screen.findByText('Convite enviado para edu@example.com.')).toBeInTheDocument();
    expect(body).toEqual({ email: 'edu@example.com', role: 'READ_ONLY', memberId: 'm-bia' });
  });
});
