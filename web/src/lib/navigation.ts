/** Full page navigation to another origin (a band host). An object, so tests can replace it. */
export const browser = {
  assign(url: string): void {
    window.location.assign(url);
  },
};

/**
 * The `redirect` query parameter, only when it is a path on this origin. Anything else (another
 * origin, `//evil.example`, `/\evil`) falls back, so the login page cannot be used as an open
 * redirect.
 */
export function safeRedirect(value: string | null, fallback: string): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return fallback;
  }
  return value;
}
