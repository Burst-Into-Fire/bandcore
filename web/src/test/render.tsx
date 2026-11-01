import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { detectHost, HostContext, type Host } from '../lib/host';
import { createRoutes } from '../routes';

export const CENTRAL: Host = detectHost('app.localhost');

export const BAND: Host = detectHost('burst.localhost');

/**
 * Renders the app at a route with every provider, as in production but without retries. The
 * host decides which routes exist (central host by default).
 */
export function renderRoute(path = '/', { host = CENTRAL }: { host?: Host } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(createRoutes(host), { initialEntries: [path] });
  const view = render(
    <HostContext value={host}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </HostContext>,
  );
  return { ...view, router, queryClient };
}
