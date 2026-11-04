import { mkdir } from 'node:fs/promises';

import { expect, test } from '@playwright/test';

import { api, expectStatus } from '../support/api.js';
import { hostUrl } from '../support/env.js';
import { accessToken, band, verifiedAccount } from '../support/seed.js';

/**
 * M7-06 acceptance: an admin registers the merch from the mockup, receives a production batch
 * that also becomes an expense split among the members, sees the stock with its low-stock
 * warnings, finds the expense in Gastos and corrects the stock after a count.
 */
test('a batch with an expense fills the stock and shows up in Gastos', async ({ page }, testInfo) => {
  const account = await verifiedAccount('Ana');
  const slug = await band(account.email, 'The Burst');
  const host = `${slug}.localhost`;
  const token = await accessToken(host, account.email);
  for (const displayName of ['Bia', 'Caio']) {
    await expectStatus(api(host, 'POST', '/members', { body: { displayName }, token }), 201);
  }

  /** For visual review in pull requests, like the shell screenshots. */
  async function snapshot(name: string) {
    await mkdir('screenshots', { recursive: true });
    const path = `screenshots/${name}.png`;
    await page.screenshot({ path, fullPage: true });
    await testInfo.attach(name, { path, contentType: 'image/png' });
  }

  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(`${hostUrl(slug)}/login`);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Senha').fill(account.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: /^Resumo/ })).toBeVisible();

  // The black T-shirt in M, G and GG.
  await page.getByRole('navigation', { name: 'Seções da banda' }).getByRole('link', { name: 'Merch' }).click();
  await page.getByRole('button', { name: 'Novo produto' }).click();
  const product = page.getByRole('dialog', { name: 'Novo produto' });
  await product.getByLabel('Nome').fill('Camiseta preta');
  await product.getByLabel('Preço de venda').pressSequentially('6000');
  for (const [index, size] of ['M', 'G', 'GG'].entries()) {
    await product.getByRole('button', { name: 'Adicionar variante' }).click();
    await product.getByRole('group', { name: `Variante ${index + 1}` }).getByLabel('Tamanho').fill(size);
  }
  await product.getByRole('button', { name: 'Salvar produto' }).click();
  await expect(product).toBeHidden();
  await expect(page.getByRole('button', { name: /^Camiseta preta/ })).toContainText('M, G, GG');

  // Ten M and three G at R$ 30,00, paid by Ana and split among the three members.
  await page.getByRole('link', { name: 'Lotes' }).click();
  await page.getByRole('button', { name: 'Novo lote' }).click();
  const batch = page.getByRole('dialog', { name: 'Novo lote de produção' });
  await batch.getByLabel('Fornecedor (opcional)').fill('Gráfica Rock');
  for (const [index, [size, quantity]] of [
    ['M', '10'],
    ['G', '3'],
  ].entries()) {
    if (index > 0) {
      await batch.getByRole('button', { name: 'Adicionar item' }).click();
    }
    const item = batch.getByRole('group', { name: `Item ${index + 1}` });
    await item.getByLabel('Variante').selectOption({ label: `Camiseta preta · ${size}` });
    await item.getByLabel('Quantidade').fill(quantity ?? '');
    await item.getByLabel('Custo unitário').pressSequentially('3000');
  }
  await expect(batch.getByRole('status').first()).toHaveText('Total: R$ 390,00');
  await batch.getByLabel('Criar um gasto para este lote').check();
  await expect(batch.getByLabel('Categoria')).toHaveValue(/.+/);
  await expect(batch.getByLabel('Quem pagou')).toHaveValue(/.+/);
  await snapshot('batch-form-1280');
  await batch.getByRole('button', { name: 'Registrar lote' }).click();
  await expect(batch).toBeHidden();
  const registered = page.getByRole('button', { name: /^Gráfica Rock/ });
  await expect(registered).toContainText('R$ 390,00');
  await expect(registered).toContainText('Gasto criado');

  // Stock: 10 un of M, 3 un of G (low), nothing of GG yet.
  await page.getByRole('link', { name: 'Estoque' }).click();
  const stock = page.locator('section', { has: page.getByRole('heading', { name: 'Estoque de merch' }) });
  const medium = stock.getByRole('button', { name: /^Camiseta preta · M/ });
  await expect(medium).toContainText('10 un');
  await expect(medium).toContainText('Custo médio R$ 30,00');
  await expect(stock.getByRole('button', { name: /^Camiseta preta · G(?!G)/ })).toContainText('Estoque baixo');
  await expect(stock.getByRole('button', { name: /^Camiseta preta · GG/ })).toContainText('Esgotado');

  // The expense is in Gastos, marked as coming from a merch batch.
  await page.goto(`${hostUrl(slug)}/expenses`);
  const expense = page.getByRole('button', { name: /Lote de merch/ }).first();
  await expect(expense).toContainText('R$ 390,00');
  await expect(expense).toContainText('Pago por Ana');
  await expense.click();
  await expect(page.locator(`#${await expense.getAttribute('aria-controls')}`)).toContainText(
    'Dividido entre 3 integrantes',
  );

  // A count after the show finds nine M: the stock follows, on a phone.
  await page.setViewportSize({ width: 360, height: 780 });
  await page.goto(`${hostUrl(slug)}/merch/stock`);
  await page.getByRole('button', { name: /^Camiseta preta · M/ }).click();
  await expect(page.getByText('Entrada de lote')).toBeVisible();
  await page.getByRole('button', { name: 'Ajustar estoque' }).click();
  const adjust = page.getByRole('dialog', { name: 'Ajustar estoque de Camiseta preta · M' });
  await adjust.getByLabel('Quantidade contada').fill('9');
  await adjust.getByRole('button', { name: 'Salvar ajuste' }).click();
  await expect(adjust).toBeHidden();
  await expect(page.getByRole('button', { name: /^Camiseta preta · M/ })).toContainText('9 un');
  await snapshot('stock-360');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
});
