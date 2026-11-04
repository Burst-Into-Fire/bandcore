import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { startSession } from '../../auth/session';
import { BAND, renderRoute } from '../../test/render';
import { apiUrl, problem, server } from '../../test/server';
import { setViewportWidth } from '../../test/viewport';

const MEMBER = ['band:read', 'member:read', 'finance:read', 'finance:write'];

// Ids sort as text in the order Ana, Bia, Caio, Dani: the order the extra cents follow.
const MEMBERS = [
  { id: 'a-ana', displayName: 'Ana', userId: 'u-ana', status: 'ACTIVE' },
  { id: 'b-bia', displayName: 'Bia', status: 'ACTIVE' },
  { id: 'c-caio', displayName: 'Caio', status: 'ACTIVE' },
  { id: 'd-dani', displayName: 'Dani', status: 'ACTIVE' },
  { id: 'e-edu', displayName: 'Edu', status: 'INACTIVE' },
].map((member) => ({ ...member, linkedToAccount: false, joinedAt: '2026-01-01T00:00:00Z' }));

const CATEGORIES = [
  { id: 'cat-gear', name: 'Equipamento', defaultKey: 'GEAR', archived: false },
  { id: 'cat-old', name: 'Antiga', archived: true },
  { id: 'cat-photo', name: 'Fotos da turnê', archived: false },
];

const ACCESS = { edit: true, delete: true, pay: false, revertPayment: false };

function expense(overrides: Record<string, unknown>) {
  return {
    id: 'e1',
    description: 'Cabos',
    categoryId: 'cat-gear',
    currency: 'BRL',
    amountCents: 10000,
    expenseDate: '2026-11-10',
    status: 'PAID',
    overdue: false,
    paidByMemberId: 'a-ana',
    paidOn: '2026-11-10',
    split: {
      mode: 'EQUAL',
      shares: [
        { memberId: 'a-ana', shareCents: 5000 },
        { memberId: 'b-bia', shareCents: 5000 },
      ],
    },
    createdBy: 'u-ana',
    version: 0,
    access: ACCESS,
    createdAt: '2026-11-10T12:00:00Z',
    updatedAt: '2026-11-10T12:00:00Z',
    ...overrides,
  };
}

function bandApi(items: unknown[], permissions = MEMBER) {
  const requests: URL[] = [];
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
    http.get(apiUrl('/expense-categories'), () => HttpResponse.json(CATEGORIES)),
    http.get(apiUrl('/expenses'), ({ request }) => {
      requests.push(new URL(request.url));
      return HttpResponse.json({
        items,
        page: 0,
        size: 50,
        totalItems: items.length,
        totalPages: 1,
      });
    }),
  );
  startSession('token');
  return requests;
}

describe('ExpensesPage', () => {
  it('lists expenses by month with the amount, the payer and the status', async () => {
    bandApi([
      expense({ id: 'e1', description: 'Van para o show', expenseDate: '2026-12-02' }),
      expense({
        id: 'e2',
        description: 'Fotógrafo',
        categoryId: 'cat-photo',
        status: 'PLANNED',
        paidByMemberId: undefined,
        expenseDate: '2026-11-25',
        amountCents: 35000,
      }),
      expense({
        id: 'e3',
        description: 'Estúdio',
        status: 'PLANNED',
        overdue: true,
        paidByMemberId: undefined,
        expenseDate: '2026-11-01',
        currency: 'USD',
        amountCents: 1999,
      }),
    ]);
    renderRoute('/expenses', { host: BAND });

    const december = await screen.findByRole('region', { name: 'Dezembro de 2026' });
    expect(within(december).getByText('Van para o show')).toBeInTheDocument();
    expect(within(december).getByText('Equipamento · Pago por Ana')).toBeInTheDocument();
    expect(within(december).getByText('Pago')).toBeInTheDocument();
    expect(within(december).getByText('R$ 100,00')).toBeInTheDocument();
    const november = screen.getByRole('region', { name: 'Novembro de 2026' });
    expect(within(november).getByText('Previsto 25/11')).toBeInTheDocument();
    expect(
      within(november).getByText('Fotos da turnê · Dividido entre 2 integrantes'),
    ).toBeVisible();
    expect(within(november).getByText('Atrasado 01/11')).toBeInTheDocument();
    expect(within(november).getByText('US$ 19,99')).toBeInTheDocument();
  });

  it('shows the shares and only the actions the user may take', async () => {
    bandApi([
      expense({
        access: { edit: false, delete: false, pay: true, revertPayment: false },
        status: 'PLANNED',
        paidByMemberId: undefined,
      }),
    ]);
    const user = userEvent.setup();
    renderRoute('/expenses', { host: BAND });

    await user.click(await screen.findByRole('button', { name: /Cabos/ }));

    expect(screen.getByText('Divisão · Dividido entre 2 integrantes')).toBeInTheDocument();
    expect(screen.getAllByText('R$ 50,00')).toHaveLength(2);
    expect(screen.getByRole('button', { name: 'Marcar como pago' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument();
  });

  it('sends the filters to the API and keeps them in the URL', async () => {
    const requests = bandApi([]);
    const user = userEvent.setup();
    const { router } = renderRoute('/expenses', { host: BAND });

    expect(await screen.findByText('Nenhum gasto registrado')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Situação'), 'Previstos');
    await user.selectOptions(screen.getByLabelText('Categoria'), 'Equipamento');

    expect(await screen.findByText('Nenhum gasto com esses filtros.')).toBeInTheDocument();
    const last = requests.at(-1);
    expect(last?.searchParams.get('status')).toBe('PLANNED');
    expect(last?.searchParams.get('categoryId')).toBe('cat-gear');
    expect(router.state.location.search).toContain('status=PLANNED');
  });

  it('records an expense paid by one member and split among four with custom amounts', async () => {
    bandApi([]);
    let body: unknown;
    server.use(
      http.post(apiUrl('/expenses'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(expense({}), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/expenses', { host: BAND });

    await user.click(await screen.findByRole('button', { name: 'Novo gasto' }));
    const form = screen.getByRole('dialog', { name: 'Novo gasto' });
    await user.type(within(form).getByLabelText('Descrição'), 'Van para o show');
    await user.selectOptions(within(form).getByLabelText('Categoria'), 'Equipamento');
    // Archived categories are not offered for new expenses.
    expect(within(form).queryByRole('option', { name: /Antiga/ })).not.toBeInTheDocument();
    await user.type(within(form).getByLabelText('Valor'), '10001');
    expect(within(form).getByLabelText('Quem pagou')).toHaveValue('a-ana');
    // Inactive members are not offered either; the four active ones share it equally.
    expect(within(form).queryByText('Edu')).not.toBeInTheDocument();
    expect(within(form).getByText('R$ 25,01')).toBeInTheDocument();
    expect(within(form).getAllByText('R$ 25,00')).toHaveLength(3);

    await user.click(within(form).getByRole('radio', { name: 'Valores' }));
    await user.type(within(form).getByLabelText('Valor de Ana'), '4000');
    await user.type(within(form).getByLabelText('Valor de Bia'), '3000');
    expect(within(form).getByRole('status')).toHaveTextContent('Faltam R$ 30,01');
    expect(within(form).getByRole('button', { name: 'Salvar gasto' })).toBeDisabled();
    await user.type(within(form).getByLabelText('Valor de Caio'), '2000');
    await user.type(within(form).getByLabelText('Valor de Dani'), '1001');
    expect(within(form).getByRole('status')).toHaveTextContent('As cotas somam o total.');
    await user.click(within(form).getByRole('button', { name: 'Salvar gasto' }));

    expect(await screen.findByRole('button', { name: 'Novo gasto' })).toBeInTheDocument();
    expect(body).toEqual({
      description: 'Van para o show',
      categoryId: 'cat-gear',
      currency: 'BRL',
      amountCents: 10001,
      expenseDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      notes: '',
      status: 'PAID',
      paidByMemberId: 'a-ana',
      split: {
        mode: 'CUSTOM_AMOUNTS',
        shares: [
          { memberId: 'a-ana', amountCents: 4000 },
          { memberId: 'b-bia', amountCents: 3000 },
          { memberId: 'c-caio', amountCents: 2000 },
          { memberId: 'd-dani', amountCents: 1001 },
        ],
      },
    });
  });

  it('splits by percentages with the same cents as the API', async () => {
    bandApi([]);
    const user = userEvent.setup();
    renderRoute('/expenses', { host: BAND });

    await user.click(await screen.findByRole('button', { name: 'Novo gasto' }));
    const form = screen.getByRole('dialog', { name: 'Novo gasto' });
    await user.type(within(form).getByLabelText('Valor'), '100');
    await user.click(within(form).getByRole('radio', { name: 'Porcentagens' }));
    await user.type(within(form).getByLabelText('Porcentagem de Ana'), '33,33');
    await user.type(within(form).getByLabelText('Porcentagem de Bia'), '33,33');
    expect(within(form).getByRole('status')).toHaveTextContent('Faltam 33,34%');
    await user.type(within(form).getByLabelText('Porcentagem de Caio'), '33,34');

    expect(within(form).getByRole('status')).toHaveTextContent('As cotas somam o total.');
    expect(within(form).getAllByText('R$ 0,33')).toHaveLength(2);
    expect(within(form).getByText('R$ 0,34')).toBeInTheDocument();
  });

  it('opens the form full screen on phones and shows API errors', async () => {
    setViewportWidth(360);
    bandApi([]);
    server.use(
      http.post(apiUrl('/expenses'), () =>
        problem(422, 'CATEGORY_ARCHIVED', [
          { field: 'categoryId', code: 'CATEGORY_ARCHIVED', message: 'archived' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderRoute('/expenses', { host: BAND });

    await user.click(await screen.findByRole('button', { name: 'Novo gasto' }));
    const form = screen.getByRole('dialog', { name: 'Novo gasto' });
    expect(form).toHaveAttribute('aria-modal', 'true');
    expect(form.className).toContain('fixed inset-0');
    expect(within(form).getByLabelText('Descrição')).toHaveFocus();

    await user.click(within(form).getByRole('button', { name: 'Salvar gasto' }));
    expect(within(form).getAllByText('Preencha este campo.').length).toBeGreaterThan(0);
    expect(within(form).getByText('Informe um valor maior que zero.')).toBeInTheDocument();

    await user.type(within(form).getByLabelText('Descrição'), 'Cabos');
    await user.selectOptions(within(form).getByLabelText('Categoria'), 'Equipamento');
    await user.type(within(form).getByLabelText('Valor'), '500');
    await user.click(within(form).getByRole('button', { name: 'Salvar gasto' }));
    expect(
      await within(form).findByText('Esta categoria foi arquivada. Escolha outra.'),
    ).toBeInTheDocument();
  });

  it('marks a planned expense as paid by the user', async () => {
    bandApi([
      expense({
        status: 'PLANNED',
        paidByMemberId: undefined,
        version: 3,
        access: { edit: false, delete: false, pay: true, revertPayment: false },
      }),
    ]);
    let body: unknown;
    server.use(
      http.post(apiUrl('/expenses/e1/pay'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(expense({}));
      }),
    );
    const user = userEvent.setup();
    renderRoute('/expenses', { host: BAND });

    await user.click(await screen.findByRole('button', { name: /Cabos/ }));
    await user.click(screen.getByRole('button', { name: 'Marcar como pago' }));
    const pay = screen.getByRole('form', { name: 'Marcar como pago' });
    // Without the right to edit, the user can only pay it themselves.
    expect(within(pay).getAllByRole('option')).toHaveLength(1);
    await user.click(within(pay).getByRole('button', { name: 'Confirmar pagamento' }));

    expect(body).toEqual({
      payerMemberId: 'a-ana',
      paidOn: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      version: 3,
    });
  });

  it('hides "Novo gasto" from read-only users', async () => {
    bandApi([], ['band:read', 'member:read', 'finance:read']);
    renderRoute('/expenses', { host: BAND });

    expect(await screen.findByText('Nenhum gasto registrado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Novo gasto' })).not.toBeInTheDocument();
  });
});
