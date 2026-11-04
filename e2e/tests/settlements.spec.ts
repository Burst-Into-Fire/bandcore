import { expect, test } from '@playwright/test';

import { api, expectStatus } from '../support/api.js';
import { hostUrl } from '../support/env.js';
import { accessToken, band, verifiedAccount } from '../support/seed.js';

/**
 * M6-05 acceptance: the vocalist paid R$ 2.700,00 split among four members; recording every
 * suggested transfer, one click each, leaves every balance at zero.
 */
test('recording every suggested transfer settles everyone', async ({ page }) => {
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
        expenseDate: '2026-11-05',
        status: 'PAID',
        paidByMemberId: ana?.id,
      },
    }),
    201,
  );

  await page.setViewportSize({ width: 360, height: 780 });
  await page.goto(`${hostUrl(slug)}/login`);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Senha').fill(account.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: /^Resumo/ })).toBeVisible();

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
