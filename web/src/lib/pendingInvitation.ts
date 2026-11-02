/**
 * The invitation token while its owner logs in, signs up or verifies their email (possibly in
 * another tab, from the verification email). Kept in this origin's localStorage for at most 7
 * days, like the invitation itself, and removed once the invitation is accepted. The token alone
 * is useless: accepting needs a session whose verified email is the invited one.
 */
const KEY = 'bandcore.pendingInvitation';
const LIFETIME_MS = 7 * 24 * 60 * 60 * 1000;

export function rememberInvitation(token: string): void {
  localStorage.setItem(KEY, JSON.stringify({ token, savedAt: Date.now() }));
}

export function pendingInvitation(): string | null {
  const raw = localStorage.getItem(KEY);
  if (!raw) {
    return null;
  }
  try {
    const saved = JSON.parse(raw) as { token?: unknown; savedAt?: unknown };
    if (
      typeof saved.token === 'string' &&
      typeof saved.savedAt === 'number' &&
      Date.now() - saved.savedAt < LIFETIME_MS
    ) {
      return saved.token;
    }
  } catch {
    // Not ours or corrupted: forget it.
  }
  forgetInvitation();
  return null;
}

export function forgetInvitation(): void {
  localStorage.removeItem(KEY);
}
