/** IANA time zone names the browser knows, plus the current one if it is missing. */
export function timeZoneOptions(current?: string): { value: string; label: string }[] {
  const zones = new Set(Intl.supportedValuesOf('timeZone'));
  if (current) {
    zones.add(current);
  }
  return [...zones]
    .sort((a, b) => a.localeCompare(b))
    .map((zone) => ({ value: zone, label: zone.replaceAll('_', ' ') }));
}
