import { mkdir } from 'node:fs/promises';

import { expect, test } from '@playwright/test';

import { api, expectStatus } from '../support/api.js';
import { hostUrl } from '../support/env.js';
import { accessToken, band, verifiedAccount } from '../support/seed.js';

/**
 * The mockup's month: the vocalist paid R$ 2.700,00 split among four members, and R$ 800,00 is
 * planned. M6-06: Resumo shows it (screenshots at 360 and 1280 px). M6-05: recording every
 * suggested transfer, one click each, leaves every balance at zero.
 */
test('the mockup month on Resumo, then every suggested transfer settles everyone', async ({
  page,
}, testInfo) => {
  // Dates in the current month (UTC); Resumo opens on it below.
  const month = new Date().toISOString().slice(0, 7);
  const account = await verifiedAccount('Ana');
  const slug = await band(account.email, 'The Burst');
  const host = `${slug}.localhost`;
  const token = await accessToken(host, account.email);
  for (const displayName of ['Bia', 'Caio', 'Dani']) {
    await expectStatus(api(host, 'POST', '/members', { body: { displayName }, token }), 201);
  }
  const categories = JSON.parse(
    (await expectStatus(api(host, 'GET', '/expense-categories', { token }), 200)).body,
  ) as { id: string }[];
  const me = JSON.parse((await expectStatus(api(host, 'GET', '/members', { token }), 200)).body) as {
    id: string;
    displayName: string;
  }[];
  const ana = me.find((member) => member.displayName === 'Ana');
  await expectStatus(
    api(host, 'POST', '/expenses', {
      token,
      body: {
        description: 'Estúdio',
        categoryId: categories[0]?.id,
        currency: 'BRL',
        amountCents: 270000,
        expenseDate: `${month}-05`,
        status: 'PAID',
        paidByMemberId: ana?.id,
      },
    }),
    201,
  );
  await expectStatus(
    api(host, 'POST', '/expenses', {
      token,
      body: {
        description: 'Fotógrafo, ensaio de divulgação',
        categoryId: categories[0]?.id,
        currency: 'BRL',
        amountCents: 80000,
        expenseDate: `${month}-25`,
        status: 'PLANNED',
      },
    }),
    201,
  );

  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(`${hostUrl(slug)}/login`);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Senha').fill(account.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: /^Resumo de / })).toBeVisible();
  // The month in UTC; São Paulo may still be in the previous one for a few hours.
  await page.goto(`${hostUrl(slug)}/?month=${month}`);

  await expect(page.getByText('R$ 3.500,00')).toBeVisible();
  await expect(page.getByText('Pago R$ 2.700,00 | Previsto R$ 800,00')).toBeVisible();
  await expect(page.getByText('Fotógrafo, ensaio de divulgação')).toBeVisible();
  await mkdir('screenshots', { recursive: true });
  for (const width of [1280, 360]) {
    await page.setViewportSize({ width, height: width < 768 ? 780 : 900 });
    await expect(page.getByText('Recebe R$ 2.025,00')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    const path = `screenshots/summary-mockup-${width}.png`;
    await page.screenshot({ path, fullPage: true });
    await testInfo.attach(`summary-mockup-${width}`, { path, contentType: 'image/png' });
  }

  const card = page.locator('section').filter({
    has: page.getByRole('heading', { name: 'Acerto entre integrantes' }),
  });
  await expect(card.getByText('Recebe R$ 2.025,00')).toBeVisible();
  await expect(card.getByText('Deve R$ 675,00')).toHaveCount(3);

  for (const name of ['Bia', 'Caio', 'Dani']) {
    await card.getByRole('button', { name: new RegExp(`^Registrar: ${name} paga`) }).click();
    await expect(card.getByRole('button', { name: new RegExp(`^Registrar: ${name} paga`) })).toHaveCount(0);
  }

  await expect(card.getByText('Tudo acertado')).toBeVisible();
  await expect(card.getByText('Quite')).toHaveCount(4);

  await card.getByRole('link', { name: 'Ver acertos' }).click();
  await expect(page.getByText('Bia pagou Ana')).toBeVisible();
  await expect(page.getByText('Caio pagou Ana')).toBeVisible();
  await expect(page.getByText('Dani pagou Ana')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
});
