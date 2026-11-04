import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { startSession } from '../../auth/session';
import { BAND, renderRoute } from '../../test/render';
import { apiUrl, problem, server } from '../../test/server';

const WRITER = ['band:read', 'member:read', 'finance:read', 'finance:write'];

const MEMBERS = [
  { id: 'a-ana', displayName: 'Ana', userId: 'u-ana', status: 'ACTIVE' },
  { id: 'b-bia', displayName: 'Bia', status: 'ACTIVE' },
  { id: 'c-caio', displayName: 'Caio', status: 'ACTIVE' },
  { id: 'd-dani', displayName: 'Dani', status: 'INACTIVE' },
].map((member) => ({ ...member, linkedToAccount: false, joinedAt: '2026-01-01T00:00:00Z' }));

/** The mockup: Ana paid R$ 2.700,00 split among four; Dani left owing her share. */
const BALANCES = {
  currencies: [
    {
      currency: 'BRL',
      members: [
        { memberId: 'a-ana', balanceCents: 202500, direction: 'RECEIVES' },
        { memberId: 'b-bia', balanceCents: -67500, direction: 'OWES' },
        { memberId: 'c-caio', balanceCents: -67500, direction: 'OWES' },
        { memberId: 'd-dani', balanceCents: -67500, direction: 'OWES', active: false },
      ].map((member) => ({
        paidCents: 0,
        shareCents: 0,
        sentCents: 0,
        receivedCents: 0,
        active: true,
        ...member,
      })),
    },
  ],
};

const PLAN = {
  currencies: [
    {
      currency: 'BRL',
      transfers: ['b-bia', 'c-caio', 'd-dani'].map((from) => ({
        fromMemberId: from,
        toMemberId: 'a-ana',
        amountCents: 67500,
      })),
    },
  ],
};

function settlement(overrides: Record<string, unknown>) {
  return {
    id: 's1',
    fromMemberId: 'b-bia',
    toMemberId: 'a-ana',
    currency: 'BRL',
    amountCents: 67500,
    paidOn: '2026-11-20',
    note: 'Pix',
    createdBy: 'u-ana',
    version: 2,
    access: { edit: true, delete: true },
    createdAt: '2026-11-20T12:00:00Z',
    ...overrides,
  };
}

function bandApi({
  permissions = WRITER,
  balances = BALANCES,
  plan = PLAN,
  history = [] as unknown[],
} = {}) {
  server.use(
    http.get(apiUrl('/me'), () =>
      HttpResponse.json({
        id: 'u-ana',
        email: 'ana@example.com',
        displayName: 'Ana',
        emailVerified: true,
        band: { id: 'b', slug: 'burst', role: 'MEMBER', permissions },
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
    http.get(apiUrl('/balances'), () => HttpResponse.json(balances)),
    http.get(apiUrl('/balances/settlement-plan'), () => HttpResponse.json(plan)),
    http.get(apiUrl('/settlements'), () =>
      HttpResponse.json({
        items: history,
        page: 0,
        size: 50,
        totalItems: history.length,
        totalPages: history.length ? 1 : 0,
      }),
    ),
  );
  startSession('token');
}

describe('SettlementsPage', () => {
  it('shows who receives and who owes, members who left included', async () => {
    bandApi();
    renderRoute('/expenses/settlements', { host: BAND });

    const card = (await screen.findByRole('heading', { name: 'Acerto entre integrantes' })).closest(
      'section',
    ) as HTMLElement;
    const brl = await within(card).findByRole('list', { name: 'Real brasileiro (BRL)' });
    expect(within(brl).getByText('Recebe R$ 2.025,00')).toBeInTheDocument();
    expect(within(brl).getAllByText('Deve R$ 675,00')).toHaveLength(3);
    expect(within(brl).getByText('Dani (saiu da banda)')).toBeInTheDocument();
    expect(within(card).getByText('Bia paga R$ 675,00 para Ana')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Acertos' })).toHaveAttribute('aria-current', 'page');
  });

  it('records a suggested transfer in one click', async () => {
    bandApi();
    let body: unknown;
    server.use(
      http.post(apiUrl('/settlements'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(settlement({}), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/expenses/settlements', { host: BAND });

    await user.click(
      await screen.findByRole('button', { name: /^Registrar: Bia paga R\$\s675,00 para Ana$/ }),
    );

    expect(await screen.findByText('Pagamento registrado.')).toBeInTheDocument();
    expect(body).toEqual({
      fromMemberId: 'b-bia',
      toMemberId: 'a-ana',
      currency: 'BRL',
      amountCents: 67500,
    });
  });

  it('says when everything is settled and hides recording from read-only users', async () => {
    bandApi({
      permissions: ['band:read', 'member:read', 'finance:read'],
      balances: {
        currencies: [
          {
            currency: 'BRL',
            members: BALANCES.currencies[0]!.members.map((member) => ({
              ...member,
              balanceCents: 0,
              direction: 'SETTLED',
            })),
          },
        ],
      },
      plan: { currencies: [] },
    });
    renderRoute('/expenses/settlements', { host: BAND });

    expect(await screen.findByText('Tudo acertado')).toBeInTheDocument();
    expect(screen.getAllByText('Quite')).toHaveLength(4);
    expect(screen.queryByRole('button', { name: 'Registrar acerto' })).not.toBeInTheDocument();
  });

  it('records a settlement by hand and validates it', async () => {
    bandApi({ plan: { currencies: [] } });
    let body: unknown;
    server.use(
      http.post(apiUrl('/settlements'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(settlement({}), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/expenses/settlements', { host: BAND });

    await user.click(await screen.findByRole('button', { name: 'Registrar acerto' }));
    const form = screen.getByRole('form', { name: 'Registrar acerto' });
    expect(within(form).getByLabelText('Quem pagou')).toHaveValue('a-ana');
    await user.selectOptions(within(form).getByLabelText('Quem recebeu'), 'Ana');
    await user.click(within(form).getByRole('button', { name: 'Salvar acerto' }));
    expect(
      await within(form).findByText('Quem paga e quem recebe precisam ser integrantes diferentes.'),
    ).toBeInTheDocument();
    expect(within(form).getByText('Informe um valor maior que zero.')).toBeInTheDocument();

    await user.selectOptions(within(form).getByLabelText('Quem pagou'), 'Bia');
    await user.type(within(form).getByLabelText('Valor'), '1500');
    await user.type(within(form).getByLabelText('Observação (opcional)'), 'Pix');
    await user.click(within(form).getByRole('button', { name: 'Salvar acerto' }));

    expect(await screen.findByRole('button', { name: 'Registrar acerto' })).toBeInTheDocument();
    expect(body).toEqual({
      fromMemberId: 'b-bia',
      toMemberId: 'a-ana',
      currency: 'BRL',
      amountCents: 1500,
      paidOn: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      note: 'Pix',
    });
  });

  it('lists the history and deletes with the version that was read', async () => {
    bandApi({
      history: [
        settlement({}),
        settlement({ id: 's2', fromMemberId: 'c-caio', access: { edit: false, delete: false } }),
      ],
    });
    let deleted: string | null = null;
    server.use(
      http.delete(apiUrl('/settlements/s1'), ({ request }) => {
        deleted = new URL(request.url).searchParams.get('version');
        return problem(409, 'VERSION_CONFLICT');
      }),
    );
    const user = userEvent.setup();
    renderRoute('/expenses/settlements', { host: BAND });

    expect(await screen.findByText('Bia pagou Ana')).toBeInTheDocument();
    expect(screen.getByText('Caio pagou Ana')).toBeInTheDocument();
    expect(screen.getAllByText('20/11 · Pix')).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Excluir' })).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Excluir' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(
      await screen.findByText(
        'Outra pessoa alterou este gasto. Recarregue a página e tente de novo.',
      ),
    ).toBeInTheDocument();
    expect(deleted).toBe('2');
  });
});
