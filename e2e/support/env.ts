/** Where the stack started by scripts/e2e.sh listens. */
export const WEB_PORT = Number(process.env.E2E_WEB_PORT ?? 3200);
export const MAILPIT_URL = process.env.E2E_MAILPIT_URL ?? 'http://127.0.0.1:8225';

export const PASSWORD = 'a long and unique e2e passphrase';

/** `http://app.localhost:3200`, or a band host with a slug. */
export function hostUrl(slug = 'app'): string {
  return `http://${slug}.localhost:${WEB_PORT}`;
}

/** Unique per test and run, so runs never collide on emails or slugs. */
export function unique(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1e6).toString(36)}`;
}
