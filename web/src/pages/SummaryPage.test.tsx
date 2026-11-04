import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { startSession } from '../auth/session';
import { BAND, renderRoute } from '../test/render';
import { apiUrl, server } from '../test/server';

const MEMBERS = [
  { id: 'a-ana', displayName: 'Ana', userId: 'u-ana', status: 'ACTIVE' },
  { id: 'b-bia', displayName: 'Bia', status: 'ACTIVE' },
  { id: 'c-caio', displayName: 'Caio', status: 'ACTIVE' },
  { id: 'd-dani', displayName: 'Dani', status: 'ACTIVE' },
].map((member) => ({ ...member, linkedToAccount: false, joinedAt: '2026-01-01T00:00:00Z' }));

function expense(id: string, description: string, amountCents: number, status: string) {
  return {
    id,
    description,
    categoryId: 'cat',
    currency: 'BRL',
    amountCents,
    expenseDate: status === 'PAID' ? '2026-11-12' : '2026-11-25',
    status,
    overdue: false,
    ...(status === 'PAID' ? { paidByMemberId: 'a-ana', paidOn: '2026-11-12' } : {}),
    split: { mode: 'EQUAL', shares: [] },
    createdBy: 'u-ana',
    version: 0,
    access: { edit: true, delete: true, pay: status !== 'PAID', revertPayment: false },
    createdAt: '2026-11-12T12:00:00Z',
    updatedAt: '2026-11-12T12:00:00Z',
  };
}

/** The mockup's month: R$ 2.700,00 paid by the vocalist, R$ 800,00 planned. */
const NOVEMBER = {
  month: '2026-11',
  currencies: [
    {
      currency: 'BRL',
      totalCents: 350000,
      paidCents: 270000,
      plannedCents: 80000,
      categories: [],
    },
    { currency: 'USD', totalCents: 2500, paidCents: 2500, plannedCents: 0, categories: [] },
  ],
  latest: [
    expense('e1', 'Fotógrafo, ensaio de divulgação', 80000, 'PLANNED'),
    expense('e2', 'Estúdio e van', 270000, 'PAID'),
  ],
  upcoming: [],
};

function bandApi(permissions = ['band:read', 'member:read', 'finance:read', 'finance:write']) {
  const months: (string | null)[] = [];
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
    http.get(apiUrl('/summary/finance'), ({ request }) => {
      const month = new URL(request.url).searchParams.get('month');
      months.push(month);
      return HttpResponse.json(
        month === '2026-10' ? { ...NOVEMBER, month, currencies: [], latest: [] } : NOVEMBER,
      );
    }),
    http.get(apiUrl('/balances'), () =>
      HttpResponse.json({
        currencies: [
          {
            currency: 'BRL',
            members: [
              ['a-ana', 202500, 'RECEIVES'],
              ['b-bia', -67500, 'OWES'],
              ['c-caio', -67500, 'OWES'],
              ['d-dani', -67500, 'OWES'],
            ].map(([memberId, balanceCents, direction]) => ({
              memberId,
              balanceCents,
              direction,
              paidCents: 0,
              shareCents: 0,
              sentCents: 0,
              receivedCents: 0,
              active: true,
            })),
          },
        ],
      }),
    ),
    http.get(apiUrl('/balances/settlement-plan'), () =>
      HttpResponse.json({
        currencies: [
          {
            currency: 'BRL',
            transfers: ['b-bia', 'c-caio', 'd-dani'].map((fromMemberId) => ({
              fromMemberId,
              toMemberId: 'a-ana',
              amountCents: 67500,
            })),
          },
        ],
      }),
    ),
  );
  startSession('token');
  return months;
}

describe('SummaryPage', () => {
  it('shows the mockup month: expenses, who receives and owes, and the latest expenses', async () => {
    bandApi();
    renderRoute('/', { host: BAND });

    expect(
      await screen.findByRole('heading', { name: 'Resumo de novembro de 2026' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('R$ 3.500,00')).toBeInTheDocument();
    expect(screen.getByText('Pago R$ 2.700,00 | Previsto R$ 800,00')).toBeInTheDocument();
    // Dollars get their own line, never added to the reais.
    expect(screen.getByText(/US\$\s25,00 · Pago US\$\s25,00 \| Previsto US\$\s0,00/)).toBeVisible();
    expect(await screen.findByText('Recebe R$ 2.025,00')).toBeInTheDocument();
    expect(screen.getAllByText('Deve R$ 675,00')).toHaveLength(3);

    const latest = screen
      .getByRole('heading', { name: 'Últimos gastos' })
      .closest('section') as HTMLElement;
    expect(within(latest).getByText('Fotógrafo, ensaio de divulgação')).toBeInTheDocument();
    expect(within(latest).getByText('Previsto 25/11')).toBeInTheDocument();
    expect(within(latest).getByText('Pago')).toBeInTheDocument();
    expect(within(latest).getByText('R$ 2.700,00')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Novo gasto' })).toHaveAttribute(
      'href',
      '/expenses?new=1',
    );
    expect(screen.getByText('Vendas de merch')).toBeInTheDocument();
  });

  it('moves between months through the URL', async () => {
    const months = bandApi();
    const user = userEvent.setup();
    const { router } = renderRoute('/', { host: BAND });

    await screen.findByRole('heading', { name: 'Resumo de novembro de 2026' });
    await user.click(screen.getByRole('button', { name: 'Mês anterior' }));

    expect(
      await screen.findByRole('heading', { name: 'Resumo de outubro de 2026' }),
    ).toBeInTheDocument();
    expect(router.state.location.search).toBe('?month=2026-10');
    expect(months).toEqual([null, '2026-10']);
    expect(await screen.findByText('Nenhum gasto neste mês')).toBeInTheDocument();
    expect(screen.getByText('R$ 0,00')).toBeInTheDocument();
  });

  it('hides "Novo gasto" from read-only users', async () => {
    bandApi(['band:read', 'member:read', 'finance:read']);
    renderRoute('/', { host: BAND });

    await screen.findByRole('heading', { name: 'Resumo de novembro de 2026' });
    expect(screen.queryByRole('link', { name: 'Novo gasto' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Registrar:/ })).not.toBeInTheDocument();
  });
});
