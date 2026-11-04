import { useId, type FormEventHandler, type ReactNode } from 'react';

import { DESKTOP, useMediaQuery } from '../lib/useMediaQuery';

/**
 * A form shown as a dialog: a card in the page on wide screens, full screen on phones (like
 * "Novo gasto"). The form owns its fields and buttons; this only frames them.
 */
export function FormDialog({
  title,
  onSubmit,
  children,
}: {
  title: string;
  onSubmit: FormEventHandler<HTMLFormElement>;
  children: ReactNode;
}) {
  const titleId = useId();
  const desktop = useMediaQuery(DESKTOP);
  return (
    <div
      role="dialog"
      aria-modal={desktop ? undefined : true}
      aria-labelledby={titleId}
      className={
        desktop
          ? 'rounded-card border-border bg-surface border p-4'
          : 'bg-background fixed inset-0 z-40 overflow-y-auto px-4 pt-4 pb-8'
      }
    >
      <form noValidate className="mx-auto flex max-w-2xl flex-col gap-4" onSubmit={onSubmit}>
        <h2 id={titleId} className="text-title font-bold">
          {title}
        </h2>
        {children}
      </form>
    </div>
  );
}
