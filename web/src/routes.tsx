import type { RouteObject } from 'react-router';

import { RequireSession } from './auth/RequireSession';
import type { Host } from './lib/host';
import { BandHomePage } from './pages/BandHomePage';
import { BandSettingsPage } from './pages/BandSettingsPage';
import { CreateBandPage } from './pages/CreateBandPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { MyBandsPage } from './pages/MyBandsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { SignupPage } from './pages/SignupPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';

/** The central host and band hosts serve the same SPA with different routes. */
export function createRoutes(host: Host): RouteObject[] {
  if (host.kind === 'band') {
    return [
      { path: '/login', element: <LoginPage /> },
      {
        element: <RequireSession />,
        children: [
          { path: '/', element: <BandHomePage /> },
          {
            path: '/settings',
            element: (
              <main className="mx-auto w-full max-w-3xl px-4 py-10">
                <BandSettingsPage />
              </main>
            ),
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ];
  }
  return [
    { path: '/', element: <HomePage /> },
    { path: '/signup', element: <SignupPage /> },
    { path: '/verify-email', element: <VerifyEmailPage /> },
    { path: '/login', element: <LoginPage /> },
    { path: '/forgot-password', element: <ForgotPasswordPage /> },
    { path: '/reset-password', element: <ResetPasswordPage /> },
    {
      element: <RequireSession />,
      children: [
        { path: '/bands', element: <MyBandsPage /> },
        { path: '/bands/new', element: <CreateBandPage /> },
      ],
    },
    { path: '*', element: <NotFoundPage /> },
  ];
}
