import { createContext, useContext } from 'react';

/**
 * Which host the app runs on. The central host is `app.{BASE_DOMAIN}`; every other subdomain is a
 * band. Each band is its own browser origin, so cookies and storage never mix between bands.
 */
export type Host =
  { kind: 'central'; baseDomain: string } | { kind: 'band'; slug: string; baseDomain: string };

const CENTRAL_LABEL = 'app';

export function detectHost(hostname: string): Host {
  const labels = hostname.toLowerCase().replace(/\.$/, '').split('.');
  const [first, ...rest] = labels;
  if (first === undefined || rest.length === 0) {
    // Bare `localhost` (for example the Vite dev server): behave as the central host.
    return { kind: 'central', baseDomain: hostname };
  }
  const baseDomain = rest.join('.');
  return first === CENTRAL_LABEL
    ? { kind: 'central', baseDomain }
    : { kind: 'band', slug: first, baseDomain };
}

/** `burst.bandcore.app`, as shown to users. */
export function bandHostName(slug: string, baseDomain: string): string {
  return `${slug}.${baseDomain}`;
}

/**
 * Absolute URL of a page on the central host, for links from a band host (account pages such as
 * password reset only exist there). Keeps the current scheme and port.
 */
export function centralUrl(host: Host, path: string): string {
  const port = window.location.port ? `:${window.location.port}` : '';
  return `${window.location.protocol}//${CENTRAL_LABEL}.${host.baseDomain}${port}${path}`;
}

export const HostContext = createContext<Host>(detectHost(window.location.hostname));

export function useHost(): Host {
  return useContext(HostContext);
}
