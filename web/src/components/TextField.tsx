import { useId, type ComponentPropsWithRef } from 'react';

type TextFieldProps = ComponentPropsWithRef<'input'> & {
  label: string;
  /** Translated error message; marks the input invalid and is read by screen readers. */
  error?: string | undefined;
  hint?: string | undefined;
};

/** Labeled input with hint and error linked through aria-describedby. */
export function TextField({ label, error, hint, id, className, ...rest }: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-card-title text-muted">
        {label}
      </label>
      <input
        {...rest}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={`border-border bg-surface-raised text-foreground focus:border-accent aria-invalid:border-warning rounded-lg border px-3 py-2 focus:outline-none ${className ?? ''}`}
      />
      {hint ? (
        <p id={hintId} className="text-caption text-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-caption text-warning">
          {error}
        </p>
      ) : null}
    </div>
  );
}
