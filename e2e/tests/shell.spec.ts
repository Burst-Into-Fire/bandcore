import { mkdir } from 'node:fs/promises';

import { expect, test } from '@playwright/test';

import { hostUrl } from '../support/env.js';
import { band, verifiedAccount } from '../support/seed.js';

/**
 * Screenshots of the band workspace at the widths the shell must support, for visual review in
 * pull requests (uploaded by CI as the "screenshots" artifact).
 */
const WIDTHS = [360, 768, 1280] as const;

test('the band workspace at 360, 768 and 1280 px', async ({ page }, testInfo) => {
  const account = await verifiedAccount('Ana');
  const slug = await band(account.email, 'The Burst');
  await mkdir('screenshots', { recursive: true });

  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto(`${hostUrl(slug)}/login`);
  await page.getByLabel('Email').fill(account.email);
  await page.getByLabel('Senha').fill(account.password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { name: /^Resumo/ })).toBeVisible();

  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: width < 768 ? 780 : 800 });
    // The last item is content that loads last, so the screenshot shows the full page.
    for (const [name, path, heading, loaded] of [
      ['summary', '/', 'Resumo', 'Acerto entre integrantes'],
      ['expenses', '/expenses', 'Gastos', 'Nenhum gasto registrado'],
      ['members', '/members', 'Integrantes', 'Acessos'],
      ['settings', '/settings', 'Configurações', 'Fuso horário'],
    ] as const) {
      await page.goto(`${hostUrl(slug)}${path}`);
      // Resumo's title names the month ("Resumo de novembro de 2026").
      await expect(page.getByRole('heading', { level: 1, name: new RegExp(`^${heading}`) })).toBeVisible();
      await expect(page.getByText(loaded, { exact: true })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Seções da banda' })).toBeVisible();
      // Nothing may overflow sideways, down to 360 px.
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
      const file = `screenshots/${name}-${width}.png`;
      await page.screenshot({ path: file });
      await testInfo.attach(`${name}-${width}`, { path: file, contentType: 'image/png' });
    }
  }
});
