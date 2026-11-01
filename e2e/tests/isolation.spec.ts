import { expect, test } from '@playwright/test';

import { api } from '../support/api.js';
import { hostUrl } from '../support/env.js';
import { accessToken, band, verifiedAccount } from '../support/seed.js';

test('a session on band A does not open band B, in the browser or through the API', async ({
  page,
}) => {
  const account = await verifiedAccount('Bia');
  const bandA = await band(account.email, 'Banda A');
  const bandB = await band(account.email, 'Banda B');

  await test.step('log in on band A', async () => {
    await page.goto(`${hostUrl(bandA)}/login`);
    await page.getByLabel('Email').fill(account.email);
    await page.getByLabel('Senha').fill(account.password);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByText('Olá, Bia!')).toBeVisible();
  });

  await test.step("band B's host shows its login page: the session cookie is host-only", async () => {
    const cookies = await page.context().cookies(hostUrl(bandA));
    const refresh = cookies.find((cookie) => cookie.name === '__Host-bandcore_rt');
    expect(refresh?.domain).toBe(`${bandA}.localhost`);
    expect(await page.context().cookies(hostUrl(bandB))).toEqual([]);

    await page.goto(`${hostUrl(bandB)}/`);
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: 'Entrar em Banda B' })).toBeVisible();
  });

  await test.step("band A's access token is refused on band B's host", async () => {
    const tokenA = await accessToken(`${bandA}.localhost`, account.email);

    expect((await api(`${bandA}.localhost`, 'GET', '/me', { token: tokenA })).status).toBe(200);
    expect((await api(`${bandB}.localhost`, 'GET', '/me', { token: tokenA })).status).toBe(401);
    expect((await api(`${bandB}.localhost`, 'GET', '/band', { token: tokenA })).status).toBe(401);
  });
});
