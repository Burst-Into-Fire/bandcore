import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

export function NotFoundPage() {
  const { t } = useTranslation();

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-start gap-4 px-4 py-10">
      <h1 className="text-title font-bold">{t('notFound.title')}</h1>
      <Link
        to="/"
        className="bg-accent text-accent-foreground hover:bg-accent-hover rounded-lg px-4 py-2 font-semibold"
      >
        {t('notFound.backHome')}
      </Link>
    </main>
  );
}
