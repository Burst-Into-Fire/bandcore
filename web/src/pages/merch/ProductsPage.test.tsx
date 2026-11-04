import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { CATALOG, MEMBER_PERMISSIONS, merchApi } from '../../test/merch';
import { BAND, renderRoute } from '../../test/render';
import { apiUrl, problem, server } from '../../test/server';
import { setViewportWidth } from '../../test/viewport';

describe('ProductsPage', () => {
  it('lists the catalog with prices, variants, stock and cost', async () => {
    merchApi({ permissions: MEMBER_PERMISSIONS });
    const user = userEvent.setup();
    renderRoute('/merch', { host: BAND });

    const shirt = await screen.findByRole('button', { name: /^Camiseta preta/ });
    expect(within(shirt).getByText('M, G, GG')).toBeInTheDocument();
    expect(within(shirt).getByText('R$ 60,00')).toBeInTheDocument();
    expect(
      within(screen.getByRole('button', { name: /^Patch/ })).getByText('Item único'),
    ).toBeInTheDocument();

    await user.click(shirt);
    const variants = screen.getByRole('list', { name: 'Variantes de Camiseta preta' });
    expect(within(variants).getByText('12 un')).toBeInTheDocument();
    expect(within(variants).getByText('Custo médio R$ 25,00')).toBeInTheDocument();
    expect(within(variants).getAllByText('Sem custo ainda')).toHaveLength(2);
    // Members see the catalog but do not change it.
    expect(screen.queryByRole('button', { name: 'Novo produto' })).not.toBeInTheDocument();
    expect(within(variants).queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Produtos' })).toHaveAttribute('aria-current', 'page');
  });

  it('registers a product with its variants, full screen on phones', async () => {
    setViewportWidth(360);
    merchApi({ catalog: [] });
    let body: unknown;
    server.use(
      http.post(apiUrl('/products'), async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(CATALOG[0], { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderRoute('/merch', { host: BAND });

    await user.click(await screen.findByRole('button', { name: 'Novo produto' }));
    const form = screen.getByRole('dialog', { name: 'Novo produto' });
    expect(form).toHaveAttribute('aria-modal', 'true');
    expect(within(form).getByLabelText('Moeda')).toHaveValue('BRL');
    await user.type(within(form).getByLabelText('Nome'), 'Camiseta preta');
    await user.type(within(form).getByLabelText('Preço de venda'), '6000');
    await user.click(within(form).getByRole('button', { name: 'Adicionar variante' }));
    await user.click(within(form).getByRole('button', { name: 'Adicionar variante' }));
    const first = within(form).getByRole('group', { name: 'Variante 1' });
    const second = within(form).getByRole('group', { name: 'Variante 2' });
    await user.type(within(first).getByLabelText('Tamanho'), 'M');
    await user.type(within(first).getByLabelText('SKU (opcional)'), 'CAM-M');
    await user.type(within(second).getByLabelText('Tamanho'), 'GG');
    await user.type(within(second).getByLabelText('Preço próprio (opcional)'), '6500');
    await user.click(within(form).getByRole('button', { name: 'Salvar produto' }));

    expect(await screen.findByRole('button', { name: 'Novo produto' })).toBeInTheDocument();
    expect(body).toEqual({
      name: 'Camiseta preta',
      description: '',
      currency: 'BRL',
      defaultPriceCents: 6000,
      variants: [
        { size: 'M', color: '', sku: 'CAM-M', lowStockThreshold: 5 },
        { size: 'GG', color: '', sku: '', lowStockThreshold: 5, priceCents: 6500 },
      ],
    });
  });

  it('points a taken SKU at its variant', async () => {
    merchApi({ catalog: [] });
    server.use(
      http.post(apiUrl('/products'), () =>
        problem(409, 'SKU_TAKEN', [
          { field: 'variants[0].sku', code: 'SKU_TAKEN', message: 'taken' },
        ]),
      ),
    );
    const user = userEvent.setup();
    renderRoute('/merch', { host: BAND });

    await user.click(await screen.findByRole('button', { name: 'Novo produto' }));
    const form = screen.getByRole('dialog', { name: 'Novo produto' });
    await user.type(within(form).getByLabelText('Nome'), 'Boné');
    await user.click(within(form).getByRole('button', { name: 'Adicionar variante' }));
    await user.type(within(form).getByLabelText('SKU (opcional)'), 'CAM-M');
    await user.click(within(form).getByRole('button', { name: 'Salvar produto' }));

    expect(await within(form).findByText('Outra variante já usa esse SKU.')).toBeInTheDocument();
    expect(within(form).getByLabelText('SKU (opcional)')).toHaveAttribute('aria-invalid', 'true');
  });

  it('says to archive a variant that already had stock', async () => {
    merchApi();
    server.use(http.delete(apiUrl('/variants/v-m'), () => problem(409, 'VARIANT_HAS_HISTORY')));
    const user = userEvent.setup();
    renderRoute('/merch', { host: BAND });

    await user.click(await screen.findByRole('button', { name: /^Camiseta preta/ }));
    const variants = screen.getByRole('list', { name: 'Variantes de Camiseta preta' });
    await user.click(
      within(variants).getAllByRole('button', { name: 'Excluir' })[0] as HTMLElement,
    );
    await user.click(
      within(screen.getByRole('group', { name: 'Excluir esta variante?' })).getByRole('button', {
        name: 'Confirmar',
      }),
    );

    expect(
      await screen.findByText('Esta variante já teve estoque. Arquive em vez de excluir.'),
    ).toBeInTheDocument();
  });
});
