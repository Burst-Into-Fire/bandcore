import { useMutation } from '@tanstack/react-query';
import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { useBandAccess } from '../auth/useBandAccess';
import { useHandoff } from '../auth/useHandoff';
import { useLogout } from '../auth/useLogout';
import { ChevronDownIcon, SwitchIcon } from '../components/icons';
import { roleLabel } from '../pages/roles';

const ITEM =
  'hover:bg-surface-raised flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left disabled:opacity-60';

/**
 * Who is logged in, plus switching band (through the central host, without the password) and
 * logging out. Closes with Escape or a click outside, and returns focus to its button.
 */
export function UserMenu({ placement }: { placement: 'up' | 'down' }) {
  const { t } = useTranslation();
  const { me, role } = useBandAccess();
  const logout = useLogout();
  const handoff = useHandoff();
  const logoutEverywhere = useMutation({
    mutationFn: async () => unwrap(await api.POST('/auth/logout-all')),
    onSettled: () => void logout(),
  });
  const [open, setOpen] = useState(false);
  const menuId = useId();
  const container = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const close = (event: Event) => {
      if (
        event instanceof KeyboardEvent
          ? event.key === 'Escape'
          : !container.current?.contains(event.target as Node)
      ) {
        setOpen(false);
        if (event instanceof KeyboardEvent) {
          button.current?.focus();
        }
      }
    };
    document.addEventListener('keydown', close);
    document.addEventListener('mousedown', close);
    return () => {
      document.removeEventListener('keydown', close);
      document.removeEventListener('mousedown', close);
    };
  }, [open]);

  const name = me.data?.displayName ?? '…';
  return (
    <div ref={container} className="relative">
      <button
        ref={button}
        type="button"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((value) => !value)}
        className="hover:bg-surface-raised flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left"
      >
        <span className="bg-surface-raised text-accent flex size-8 shrink-0 items-center justify-center rounded-full font-bold">
          {name.charAt(0).toUpperCase()}
        </span>
        <span className="hidden min-w-0 flex-1 flex-col md:flex">
          <span className="truncate font-medium">{name}</span>
          {role ? <span className="text-caption text-muted">{roleLabel(t, role)}</span> : null}
        </span>
        <span className="sr-only md:hidden">{t('shell.userMenu', { name })}</span>
        <ChevronDownIcon className="text-muted size-4 shrink-0" />
      </button>
      {open ? (
        <div
          id={menuId}
          className={`border-border bg-surface absolute right-0 z-30 flex w-64 flex-col gap-1 rounded-lg border p-2 shadow-lg ${
            placement === 'up' ? 'bottom-full left-0 mb-2' : 'top-full mt-2'
          }`}
        >
          <p className="text-caption text-muted truncate px-3 py-1">{me.data?.email}</p>
          <button
            type="button"
            className={ITEM}
            disabled={handoff.isPending}
            onClick={() => handoff.mutate(undefined)}
          >
            <SwitchIcon className="size-4" />
            {t('shell.switchBand')}
          </button>
          <button type="button" className={ITEM} onClick={() => void logout()}>
            {t('common.logout')}
          </button>
          <button
            type="button"
            className={ITEM}
            disabled={logoutEverywhere.isPending}
            onClick={() => logoutEverywhere.mutate()}
          >
            {t('common.logoutEverywhere')}
          </button>
        </div>
      ) : null}
    </div>
  );
}
