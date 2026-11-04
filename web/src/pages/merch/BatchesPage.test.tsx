import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';

import type { components } from '../../api/schema';
import { MEMBER_PERMISSIONS, merchApi } from '../../test/merch';
import { BAND, renderRoute } from '../../test/render';
import { apiUrl, problem, server } from '../../test/server';

type Batch = components['schemas']['BatchResponse'];

function batch(overrides: Partial<Batch>): Batch {
  return {
    id: 'batch-1',
    supplier: 'Gráfica Rock',
    receivedOn: '2026-11-10',
    currency: 'BRL',
    costMode: 'PER_ITEM',
    totalCostCents: 46_000,
    status: 'RECEIVED',
    expenseRequested: false,
    cancellable: true,
    version: 0,
    createdAt: '2026-11-10T15:00:00Z',
    items: [
      {
        id: 'i1',
        variantId: 'v-m',
        productId: 'p-shirt',
        productName: 'Camiseta preta',
        size: 'M',
        quantity: 10,
        lineCostCents: 30_000,
      },
      {
        id: 'i2',
        variantId: 'v-g',
        productId: 'p-shirt',
        productName: 'Camiseta preta',
        size: 'G',
        quantity: 5,
        lineCostCents: 16_000,
      },
    ],
    ...overrides,
  };
}

async function openForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Novo lote' }));
  return screen.getByRole('dialog', { name: 'Novo lote de produção' });
}

describe('BatchesPage', () => {
  it('shows how an allocated total splits among the items, to the cent', async () => {
    merchApi();
    const user = userEvent.setup();
    renderRoute('/merch/batches', { host: BAND });
    const form = await openForm(user);

    await user.click(within(form).getByLabelText('Total do lote, rateado por quantidade'));
    await user.type(within(form).getByLabelText('Custo total do lote'), '10000');
    await user.click(within(form).getByRole('button', { name: 'Adicionar item' }));
    await user.click(within(form).getByRole('button', { name: 'Adicionar item' }));

    const lines = [1, 2, 3].map((number) =>
      within(within(form).getByRole('group', { name: `Item ${number}` })).getByText(/^Custo:/),
    );
    expect(lines.map((line) => line.textContent?.replace(/\s+/g, ' '))).toEqual([
      'Custo: R$ 33,34',
      'Custo: R$ 33,33',
      'Custo: R$ 33,33',
    ]);
    expect(within(form).getByRole('status')).toHaveTextContent('Total: R$ 100,00');
    // Items cost per unit only when costs are given per item.
    expect(within(form).queryByLabelText('Custo unitário')).not.toBeInTheDocument();
  });

  it('registers a batch with the expense that pays for it', async () => {
    merchApi();
    let body: unknown;
    server.use(
      http.post(apiUrl('/batches'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(batch({ expenseRequested: true }), { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/merch/batches', { host: BAND });
    const form = await openForm(user);

    await user.type(within(form).getByLabelText('Fornecedor (opcional)'), 'Gráfica Rock');
    const item = within(form).getByRole('group', { name: 'Item 1' });
    await user.selectOptions(within(item).getByLabelText('Variante'), 'Camiseta preta · M');
    await user.clear(within(item).getByLabelText('Quantidade'));
    await user.type(within(item).getByLabelText('Quantidade'), '10');
    await user.type(within(item).getByLabelText('Custo unitário'), '3000');
    expect(within(item).getByText('Custo: R$ 300,00')).toBeInTheDocument();
    // Archived and other currencies' variants are not offered.
    expect(
      within(item)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual([
      'Escolha…',
      'Camiseta preta · M',
      'Camiseta preta · G',
      'Camiseta preta · GG',
      'Patch',
    ]);

    await user.click(within(form).getByLabelText('Criar um gasto para este lote'));
    expect(within(form).getByLabelText('Descrição do gasto')).toHaveValue('Lote de merch');
    expect(within(form).getByLabelText('Categoria')).toHaveValue('cat-merch');
    expect(within(form).getByLabelText('Quem pagou')).toHaveValue('a-ana');
    await user.click(within(form).getByRole('button', { name: 'Registrar lote' }));

    expect(await screen.findByRole('button', { name: 'Novo lote' })).toBeInTheDocument();
    expect(body).toEqual({
      supplier: 'Gráfica Rock',
      receivedOn: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      currency: 'BRL',
      costMode: 'PER_ITEM',
      notes: '',
      items: [{ variantId: 'v-m', quantity: 10, unitCostCents: 3000 }],
      expense: {
        description: 'Lote de merch',
        categoryId: 'cat-merch',
        paid: true,
        paidByMemberId: 'a-ana',
        split: {
          mode: 'EQUAL',
          shares: [{ memberId: 'a-ana' }, { memberId: 'b-bia' }, { memberId: 'c-caio' }],
        },
      },
    });
  });

  it('shows the expense errors next to their fields', async () => {
    merchApi();
    server.use(
      http.post(apiUrl('/batches'), () =>
        problem(422, 'MEMBER_INACTIVE', [
          { field: 'expense.paidByMemberId', code: 'MEMBER_INACTIVE', message: 'left' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderRoute('/merch/batches', { host: BAND });
    const form = await openForm(user);

    const item = within(form).getByRole('group', { name: 'Item 1' });
    await user.selectOptions(within(item).getByLabelText('Variante'), 'Patch');
    await user.type(within(item).getByLabelText('Custo unitário'), '500');
    await user.click(within(form).getByLabelText('Criar um gasto para este lote'));
    await user.click(within(form).getByRole('button', { name: 'Registrar lote' }));

    const payer = within(form).getByLabelText('Quem pagou');
    expect(await within(form).findByText('Este integrante saiu da banda.')).toBeInTheDocument();
    expect(payer).toHaveAttribute('aria-invalid', 'true');
  });

  it('lists batches and cancels the latest with a warning about its expense', async () => {
    merchApi({ batches: [batch({ expenseRequested: true })] });
    let cancelled: unknown;
    server.use(
      http.post(apiUrl('/batches/batch-1/cancel'), async ({ request }) => {
        cancelled = await request.json();
        return HttpResponse.json(batch({ status: 'CANCELLED', cancellable: false }));
      }),
    );
    const user = userEvent.setup();
    renderRoute('/merch/batches', { host: BAND });

    const row = await screen.findByRole('button', { name: /^Gráfica Rock/ });
    expect(within(row).getByText('R$ 460,00')).toBeInTheDocument();
    expect(within(row).getByText('Gasto criado')).toBeInTheDocument();
    await user.click(row);
    expect(screen.getByText('10 × Camiseta preta · M')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cancelar lote' }));
    const question = screen.getByRole('group', { name: /O gasto em Gastos não muda/ });
    await user.click(within(question).getByRole('button', { name: 'Confirmar' }));

    await vi.waitFor(() => expect(cancelled).toEqual({ version: 0 }));
  });

  it('hides registering and cancelling from members', async () => {
    merchApi({ permissions: MEMBER_PERMISSIONS, batches: [batch({})] });
    const user = userEvent.setup();
    renderRoute('/merch/batches', { host: BAND });

    await user.click(await screen.findByRole('button', { name: /^Gráfica Rock/ }));

    expect(screen.queryByRole('button', { name: 'Novo lote' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancelar lote' })).not.toBeInTheDocument();
  });
});
