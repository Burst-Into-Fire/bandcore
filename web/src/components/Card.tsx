import type { ReactNode } from 'react';

/** Surface card from the mockup, with an optional title and an action on the right. */
export function Card({
  title,
  action,
  children,
  className,
}: {
  title?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={`rounded-card border-border bg-surface flex flex-col gap-3 border p-4 ${className ?? ''}`}
    >
      {title || action ? (
        <header className="flex items-center justify-between gap-3">
          {title ? <h2 className="text-card-title text-muted font-semibold">{title}</h2> : <span />}
          {action}
        </header>
      ) : null}
      {children}
    </section>
  );
}

/** A KPI tile: label, big value and a caption (for example the comparison with last month). */
export function KpiTile({
  label,
  value,
  caption,
}: {
  label: string;
  value: string;
  caption?: string;
}) {
  return (
    <div className="rounded-card border-border bg-surface flex flex-col gap-1 border p-4">
      <p className="text-card-title text-muted">{label}</p>
      <p className="text-kpi font-bold tabular-nums">{value}</p>
      {caption ? <p className="text-caption text-muted">{caption}</p> : null}
    </div>
  );
}

/** Rows with a label on the left and a value aligned to the right (amounts, quantities). */
export function ValueList({
  items,
}: {
  items: readonly { key: string; label: ReactNode; detail?: ReactNode; value: ReactNode }[];
}) {
  return (
    <ul className="divide-border flex flex-col divide-y">
      {items.map((item) => (
        <li key={item.key} className="flex items-center justify-between gap-3 py-2">
          <div className="flex min-w-0 flex-col">
            <span className="truncate">{item.label}</span>
            {item.detail ? <span className="text-caption text-muted">{item.detail}</span> : null}
          </div>
          <span className="shrink-0 text-right tabular-nums">{item.value}</span>
        </li>
      ))}
    </ul>
  );
}

const BADGE_TONES = {
  success: 'border-success/40 text-success',
  warning: 'border-warning/40 text-warning',
  neutral: 'border-border text-muted',
  accent: 'border-accent/40 text-accent',
} as const;

/** Small status label (paid/planned, active/inactive, pending/expired). */
export function StatusBadge({
  tone,
  children,
}: {
  tone: keyof typeof BADGE_TONES;
  children: ReactNode;
}) {
  return (
    <span
      className={`text-caption inline-flex items-center rounded-full border px-2 py-0.5 font-medium ${BADGE_TONES[tone]}`}
    >
      {children}
    </span>
  );
}
