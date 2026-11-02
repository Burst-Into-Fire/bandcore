import { expect, test } from '@playwright/test';

import { hostUrl, PASSWORD, unique } from '../support/env.js';
import { awaitLink } from '../support/mailpit.js';

test('sign up, verify, create a band, log in on its host and log out', async ({ page }) => {
  const email = `${unique('onboarding')}@example.com`;
  const slug = unique('banda').slice(0, 32);

  await test.step('sign up on the central host', async () => {
    await page.goto(`${hostUrl()}/signup`);
    await page.getByLabel('Seu nome').fill('Ana');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Senha').fill(PASSWORD);
    await page.getByRole('button', { name: 'Criar conta' }).click();
    await expect(page.getByRole('heading', { name: 'Verifique seu email' })).toBeVisible();
  });

  await test.step('open the verification link from the email', async () => {
    const link = await awaitLink(email, '/verify-email#token=');
    await page.goto(link);
    await expect(page.getByText('Email confirmado! Agora você já pode entrar.')).toBeVisible();
    await expect(page).toHaveURL(`${hostUrl()}/verify-email`);
    await page.getByRole('link', { name: 'Entrar' }).click();
  });

  await test.step('log in on the central host and create a band', async () => {
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Senha').fill(PASSWORD);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByRole('heading', { name: 'Minhas bandas' })).toBeVisible();
    await page.getByRole('link', { name: 'Criar banda' }).click();
    await page.getByLabel('Nome da banda').fill('Banda E2E');
    await page.getByLabel('Endereço da banda').fill(slug);
    await expect(page.getByText('Disponível')).toBeVisible();
    await page.getByRole('button', { name: 'Criar banda' }).click();
  });

  await test.step('log in on the band host', async () => {
    await expect(page).toHaveURL(`${hostUrl(slug)}/login`);
    await expect(page.getByRole('heading', { name: 'Entrar em Banda E2E' })).toBeVisible();
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Senha').fill(PASSWORD);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByText('Olá, Ana!')).toBeVisible();
    await expect(page.getByText('Seu papel nesta banda: Administrador')).toBeVisible();
  });

  await test.step('log out', async () => {
    await page.getByRole('button', { name: /Ana/ }).click();
    await page.getByRole('button', { name: 'Sair', exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`^${hostUrl(slug)}/login`));
    await expect(page.getByRole('heading', { name: 'Entrar em Banda E2E' })).toBeVisible();
    // The refresh cookie was revoked: a fresh page load does not bring the session back.
    await page.goto(`${hostUrl(slug)}/`);
    await expect(page).toHaveURL(new RegExp(`^${hostUrl(slug)}/login`));
    await expect(page.getByRole('heading', { name: 'Entrar em Banda E2E' })).toBeVisible();
  });
});
