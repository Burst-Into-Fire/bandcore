import { expect, test } from '@playwright/test';

import { hostUrl, PASSWORD, unique } from '../support/env.js';
import { awaitLink } from '../support/mailpit.js';
import { band, verifiedAccount } from '../support/seed.js';

test('an admin invites someone, who signs up from the email and lands in the band as a member', async ({
  browser,
  page,
}) => {
  const admin = await verifiedAccount('Ana');
  const slug = await band(admin.email, 'Banda Convite');
  const invitee = `${unique('invitee')}@example.com`;

  await test.step('the admin invites an email as a member', async () => {
    await page.goto(`${hostUrl(slug)}/login`);
    await page.getByLabel('Email').fill(admin.email);
    await page.getByLabel('Senha').fill(admin.password);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await page.getByRole('navigation', { name: 'Seções da banda' }).getByRole('link', { name: 'Integrantes' }).click();
    const form = page.getByRole('form', { name: 'Convites' });
    await form.getByLabel('Email').fill(invitee);
    await form.getByLabel('Papel').selectOption('MEMBER');
    await form.getByRole('button', { name: 'Convidar' }).click();
    await expect(page.getByText(`Convite enviado para ${invitee}.`)).toBeVisible();
    await expect(page.getByText('Pendente')).toBeVisible();
  });

  // The invitee uses another browser: nothing is shared with the admin's session.
  const context = await browser.newContext();
  const guest = await context.newPage();

  await test.step('the invitee opens the link and signs up with the invited email', async () => {
    await guest.goto(await awaitLink(invitee, '/invitations/accept#token='));
    await expect(guest.getByRole('heading', { name: 'Convite para Banda Convite' })).toBeVisible();
    await guest.getByRole('link', { name: 'Criar conta' }).click();
    await expect(guest.getByLabel('Email')).toHaveValue(invitee);
    await guest.getByLabel('Seu nome').fill('Bia');
    await guest.getByLabel('Senha').fill(PASSWORD);
    await guest.getByRole('button', { name: 'Criar conta' }).click();
    await expect(guest.getByRole('heading', { name: 'Verifique seu email' })).toBeVisible();
  });

  await test.step('verifies the email and goes back to the invitation', async () => {
    await guest.goto(await awaitLink(invitee, '/verify-email#token='));
    await expect(guest.getByText('Email confirmado! Agora você já pode entrar.')).toBeVisible();
    await guest.getByRole('link', { name: 'Continuar para o convite' }).click();
    await guest.getByRole('link', { name: 'Entrar' }).click();
    await guest.getByLabel('Email').fill(invitee);
    await guest.getByLabel('Senha').fill(PASSWORD);
    await guest.getByRole('button', { name: 'Entrar' }).click();
  });

  await test.step('accepts and lands in the band as a member, without logging in again', async () => {
    await guest.getByRole('button', { name: 'Aceitar convite' }).click();
    await expect(guest).toHaveURL(`${hostUrl(slug)}/`);
    await expect(guest.getByText('Seu papel nesta banda: Integrante')).toBeVisible();
  });

  await test.step('the admin sees the invitation accepted and Bia as a member', async () => {
    await page.reload();
    await expect(page.getByText('Aceito')).toBeVisible();
    await expect(page.getByText('Bia', { exact: true }).first()).toBeVisible();
  });

  await context.close();
});
