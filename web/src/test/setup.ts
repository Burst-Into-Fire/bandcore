import '@testing-library/jest-dom/vitest';
import '../i18n';

import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { resetSessionState } from '../auth/session';
import { server } from './server';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetSessionState();
});
afterAll(() => server.close());
