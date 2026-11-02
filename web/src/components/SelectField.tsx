import { useId, type ComponentPropsWithRef } from 'react';

type SelectFieldProps = ComponentPropsWithRef<'select'> & {
  label: string;
  /** Translated error message; marks the select invalid and is read by screen readers. */
  error?: string | undefined;
  hint?: string | undefined;
  options: readonly { value: string; label: string }[];
};

/** Labeled native select with hint and error linked through aria-describedby. */
export function SelectField({
  label,
  error,
  hint,
  options,
  id,
  className,
  ...rest
}: SelectFieldProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const hintId = `${selectId}-hint`;
  const errorId = `${selectId}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={selectId} className="text-card-title text-muted">
        {label}
      </label>
      <select
        {...rest}
        id={selectId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={`border-border bg-surface-raised text-foreground focus:border-accent aria-invalid:border-warning rounded-lg border px-3 py-2 focus:outline-none ${className ?? ''}`}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
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
