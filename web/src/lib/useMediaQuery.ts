import { useSyncExternalStore } from 'react';

/** Whether a media query matches, kept up to date as the window changes. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    () => window.matchMedia(query).matches,
  );
}

/** Desktop layout (sidebar) from 768 px; below that, compact header and bottom navigation. */
export const DESKTOP = '(min-width: 768px)';
