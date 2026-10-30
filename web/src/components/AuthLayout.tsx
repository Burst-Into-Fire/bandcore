import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

/** Centered single-column layout for the sign up, login and band creation pages. */
export function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string | undefined;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md flex-col gap-6 px-4 py-10">
      <header className="flex flex-col gap-1">
        <p className="text-card-title text-accent font-semibold tracking-wide uppercase">
          {t('app.name')}
        </p>
        <h1 className="text-title font-bold">{title}</h1>
        {subtitle ? <p className="text-muted">{subtitle}</p> : null}
      </header>
      {children}
    </main>
  );
}
