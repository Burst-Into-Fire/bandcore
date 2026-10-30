import './index.css';
import './i18n';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';

import { detectHost, HostContext } from './lib/host';
import { createRoutes } from './routes';

const host = detectHost(window.location.hostname);
const queryClient = new QueryClient();
const router = createBrowserRouter(createRoutes(host));

const root = document.getElementById('root');
if (!root) {
  throw new Error('Missing #root element');
}

createRoot(root).render(
  <StrictMode>
    <HostContext value={host}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </HostContext>
  </StrictMode>,
);
