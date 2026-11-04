import type { RouteObject } from 'react-router';

import { RequireSession } from './auth/RequireSession';
import type { Host } from './lib/host';
import { AcceptInvitationPage } from './pages/AcceptInvitationPage';
import { BandSettingsPage } from './pages/BandSettingsPage';
import { ComingSoonPage } from './pages/ComingSoonPage';
import { CreateBandPage } from './pages/CreateBandPage';
import { ExpensesPage } from './pages/expenses/ExpensesPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { HandoffPage } from './pages/HandoffPage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { MembersPage } from './pages/members/MembersPage';
import { MyBandsPage } from './pages/MyBandsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { SettlementsPage } from './pages/settlements/SettlementsPage';
import { SignupPage } from './pages/SignupPage';
import { SummaryPage } from './pages/SummaryPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';
import { AppShell } from './shell/AppShell';

/** The central host and band hosts serve the same SPA with different routes. */
export function createRoutes(host: Host): RouteObject[] {
  if (host.kind === 'band') {
    return [
      { path: '/login', element: <LoginPage /> },
      { path: '/auth/handoff', element: <HandoffPage /> },
      {
        element: <RequireSession />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <SummaryPage /> },
              { path: '/expenses', element: <ExpensesPage /> },
              { path: '/expenses/settlements', element: <SettlementsPage /> },
              { path: '/merch', element: <ComingSoonPage section="merch" /> },
              { path: '/members', element: <MembersPage /> },
              { path: '/settings', element: <BandSettingsPage /> },
            ],
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
    { path: '/auth/handoff', element: <HandoffPage /> },
    { path: '/forgot-password', element: <ForgotPasswordPage /> },
    { path: '/reset-password', element: <ResetPasswordPage /> },
    { path: '/invitations/accept', element: <AcceptInvitationPage /> },
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
