import { api, expectStatus } from './api.js';
import { PASSWORD, unique } from './env.js';
import { awaitLink, tokenOf } from './mailpit.js';

const CENTRAL = 'app.localhost';

/** A verified account created through the API (signup, then the link from Mailpit). */
export async function verifiedAccount(name = 'E2E'): Promise<{ email: string; password: string }> {
  const email = `${unique('e2e')}@example.com`;
  await expectStatus(
    api(CENTRAL, 'POST', '/auth/signup', { body: { email, password: PASSWORD, displayName: name } }),
    202,
  );
  const token = tokenOf(await awaitLink(email, '/verify-email#token='));
  await expectStatus(api(CENTRAL, 'POST', '/auth/verify-email', { body: { token } }), 204);
  return { email, password: PASSWORD };
}

/** Logs in on a host through the API and returns the access token. */
export async function accessToken(host: string, email: string): Promise<string> {
  const response = await expectStatus(
    api(host, 'POST', '/auth/login', { body: { email, password: PASSWORD } }),
    200,
  );
  return (JSON.parse(response.body) as { accessToken: string }).accessToken;
}

/** A band created by the account, which becomes its admin. Returns the slug. */
export async function band(email: string, name: string): Promise<string> {
  const slug = unique('band').slice(0, 32);
  const token = await accessToken(CENTRAL, email);
  await expectStatus(api(CENTRAL, 'POST', '/bands', { body: { name, slug }, token }), 201);
  return slug;
}
