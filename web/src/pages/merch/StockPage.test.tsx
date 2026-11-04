import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { MEMBER_PERMISSIONS, merchApi } from '../../test/merch';
import { BAND, renderRoute } from '../../test/render';
import { apiUrl, problem, server } from '../../test/server';
import { setViewportWidth } from '../../test/viewport';

const MOVEMENTS = {
  items: [
    {
      id: 'mv2',
      type: 'ADJUSTMENT',
      quantityDelta: -2,
      quantityAfter: 3,
      reason: 'DAMAGED',
      note: 'Rasgou no show',
      occurredAt: '2026-11-20T23:00:00Z',
    },
    {
      id: 'mv1',
      type: 'BATCH_IN',
      quantityDelta: 5,
      quantityAfter: 5,
      sourceType: 'BATCH',
      sourceId: 'batch-1',
      occurredAt: '2026-11-10T15:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalItems: 2,
  totalPages: 1,
};

describe('StockPage', () => {
  it('lists the stock of every variant with low stock in coral', async () => {
    merchApi({ permissions: MEMBER_PERMISSIONS });
    renderRoute('/merch/stock', { host: BAND });

    const card = (await screen.findByRole('heading', { name: 'Estoque de merch' })).closest(
      'section',
    ) as HTMLElement;
    expect(within(card).getByText('12 un')).not.toHaveClass('text-warning');
    expect(within(card).getByText('3 un')).toHaveClass('text-warning');
    expect(within(card).getByText('Estoque baixo')).toHaveClass('text-warning');
    expect(within(card).getByText('0 un')).toHaveClass('text-warning');
    expect(within(card).getByText('Esgotado')).toBeInTheDocument();
    expect(within(card).getByText('Patch')).toBeInTheDocument();
    expect(within(card).getByText('Camiseta preta · M')).toBeInTheDocument();
    expect(within(card).getByText('Custo médio R$ 25,00')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Estoque' })).toHaveAttribute('aria-current', 'page');
  });

  it('shows the history of a variant, and only admins adjust', async () => {
    merchApi({ permissions: MEMBER_PERMISSIONS });
    server.use(http.get(apiUrl('/variants/v-g/movements'), () => HttpResponse.json(MOVEMENTS)));
    const user = userEvent.setup();
    renderRoute('/merch/stock', { host: BAND });

    await user.click(await screen.findByRole('button', { name: /^Camiseta preta · G(?!G)/ }));

    const history = await screen.findByRole('list', { name: 'Movimentações' });
    expect(within(history).getByText('Ajuste · Avaria')).toBeInTheDocument();
    expect(within(history).getByText('Rasgou no show')).toBeInTheDocument();
    expect(within(history).getByText('−2')).toBeInTheDocument();
    expect(within(history).getByText('ficou 3 un')).toBeInTheDocument();
    expect(within(history).getByText('Entrada de lote')).toBeInTheDocument();
    expect(within(history).getByText('+5')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajustar estoque' })).not.toBeInTheDocument();
  });

  it('adjusts to a counted quantity and shows when the stock is not enough', async () => {
    setViewportWidth(360);
    merchApi();
    let body: unknown;
    server.use(
      http.get(apiUrl('/variants/v-m/movements'), () =>
        HttpResponse.json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }),
      ),
      http.post(apiUrl('/variants/v-m/adjustments'), async ({ request }) => {
        body = await request.json();
        if ((body as { quantityDelta?: number }).quantityDelta !== undefined) {
          return problem(409, 'INSUFFICIENT_STOCK');
        }
        return HttpResponse.json({ id: 'mv3' }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/merch/stock', { host: BAND });

    await user.click(await screen.findByRole('button', { name: /^Camiseta preta · M/ }));
    await user.click(await screen.findByRole('button', { name: 'Ajustar estoque' }));
    const form = screen.getByRole('dialog', { name: 'Ajustar estoque de Camiseta preta · M' });
    expect(form).toHaveAttribute('aria-modal', 'true');
    expect(within(form).getByText('Em estoque: 12 un')).toBeInTheDocument();

    await user.click(within(form).getByLabelText('Entrada ou saída'));
    await user.clear(within(form).getByLabelText('Quantidade'));
    await user.type(within(form).getByLabelText('Quantidade'), '-20');
    await user.selectOptions(within(form).getByLabelText('Motivo'), 'Perda');
    await user.click(within(form).getByRole('button', { name: 'Salvar ajuste' }));

    expect(
      await within(form).findByText('Não há estoque suficiente para isso.'),
    ).toBeInTheDocument();
    expect(body).toEqual({ quantityDelta: -20, reason: 'LOST', note: '' });

    await user.click(within(form).getByLabelText('Contei o estoque'));
    await user.clear(within(form).getByLabelText('Quantidade contada'));
    await user.type(within(form).getByLabelText('Quantidade contada'), '10');
    await user.selectOptions(within(form).getByLabelText('Motivo'), 'Outro');
    await user.click(within(form).getByRole('button', { name: 'Salvar ajuste' }));

    expect(await within(form).findByText('Diga o que aconteceu.')).toBeInTheDocument();
    await user.type(within(form).getByLabelText('Observação (opcional)'), 'Contagem pós-show');
    await user.click(within(form).getByRole('button', { name: 'Salvar ajuste' }));

    expect(await screen.findByRole('button', { name: /^Camiseta preta · M/ })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(body).toEqual({ countedQuantity: 10, reason: 'OTHER', note: 'Contagem pós-show' });
  });

  it('says how to start when there is no merch yet', async () => {
    merchApi({ catalog: [] });
    renderRoute('/merch/stock', { host: BAND });

    expect(await screen.findByText('Nenhum produto no estoque')).toBeInTheDocument();
  });
});
