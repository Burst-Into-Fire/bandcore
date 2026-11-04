import { mkdir } from 'node:fs/promises';

import { expect, test } from '@playwright/test';

import { api, expectStatus } from '../support/api.js';
import { hostUrl } from '../support/env.js';
import { accessToken, band, verifiedAccount } from '../support/seed.js';

/**
 * M5-06 acceptance: on a phone, a member records an expense paid by one member and split among
 * four, and the list shows it with the right shares (R$ 100,01 = 25,01 + 25,00 + 25,00 + 25,00).
 */
test('an expense paid by one member and split among four, from a phone', async ({ page }, testInfo) => {
  const account = await verifiedAccount('Ana');
  const slug = await band(account.email, 'The Burst');
  const host = `${slug}.localhost`;
  const token = await accessToken(host, account.email);
  for (const displayName of ['Bia', 'Caio', 'Dani']) {
    await expectStatus(api(host, 'POST', '/members', { body: { displayName }, token }), 201);
  }

  /** For visual review in pull requests, like the shell screenshots. */
  async function snapshot(name: string) {
    await mkdir('screenshots', { recursive: true });
    const path = `screenshots/${name}.png`;
    await page.screenshot({ path, fullPage: true });
    await testInfo.attach(name, { path, contentType: 'image/png' });
  }

  await page.setViewportSize({ width: 360, height: 780 });
  await page.goto(`${hostUrl(slug)}/login`);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Senha').fill(account.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: 'Resumo' })).toBeVisible();

  const started = Date.now();
  await page.getByRole('link', { name: 'Novo gasto' }).click();
  const form = page.getByRole('dialog', { name: 'Novo gasto' });
  await expect(form).toBeVisible();
  await form.getByLabel('Descrição').fill('Van para o show');
  await form.getByLabel('Categoria').selectOption({ label: 'Equipamento' });
  await form.getByLabel('Valor', { exact: true }).pressSequentially('10001');
  await expect(form.getByLabel('Quem pagou')).toHaveValue(/.+/);
  await expect(form.getByRole('status')).toHaveText('As cotas somam o total.');
  await snapshot('expense-form-360');
  await form.getByRole('button', { name: 'Salvar gasto' }).click();
  await expect(form).toBeHidden();
  expect(Date.now() - started).toBeLessThan(60_000);

  const row = page.getByRole('button', { name: /Van para o show/ });
  await expect(row).toContainText('R$ 100,01');
  await expect(row).toContainText('Equipamento · Pago por Ana');
  await expect(row).toContainText('Pago');
  await row.click();
  const details = page.locator(`#${await row.getAttribute('aria-controls')}`);
  await expect(details).toContainText('Dividido entre 4 integrantes');
  for (const name of ['Ana', 'Bia', 'Caio', 'Dani']) {
    await expect(details.getByText(name, { exact: true })).toBeVisible();
  }
  await expect(details.getByText('R$ 25,01', { exact: true })).toHaveCount(1);
  await expect(details.getByText('R$ 25,00', { exact: true })).toHaveCount(3);
  await snapshot('expense-list-360');
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
});
