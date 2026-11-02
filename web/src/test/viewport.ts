let width = 1280;

/** The window width that matchMedia answers for in tests (1280 px unless a test changes it). */
export function setViewportWidth(value: number): void {
  width = value;
}

export function resetViewport(): void {
  width = 1280;
}

/** Enough of matchMedia for min-width and max-width queries. */
export function matchMedia(query: string): MediaQueryList {
  const min = /min-width:\s*(\d+)px/.exec(query);
  const max = /max-width:\s*(\d+)px/.exec(query);
  const matches = (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
  return {
    matches,
    media: query,
    onchange: null,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
  };
}
