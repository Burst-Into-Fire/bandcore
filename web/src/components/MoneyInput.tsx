import { useId, type ComponentPropsWithoutRef } from 'react';

import { formatAmount } from '../lib/money';

/** Up to 13 digits (R$ 99.999.999.999,99), far below Number.MAX_SAFE_INTEGER. */
const MAX_DIGITS = 13;

type MoneyInputProps = Omit<
  ComponentPropsWithoutRef<'input'>,
  'value' | 'onChange' | 'type' | 'inputMode'
> & {
  label: string;
  /** Amount in integer cents. */
  value: number;
  onChange: (cents: number) => void;
};

/**
 * Cash-register style input: every typed digit enters from the right, so "3", "5", "0", "0",
 * "0", "0" shows 3.500,00 and reports 350000 cents. The value never goes through floating point.
 */
export function MoneyInput({ label, value, onChange, id, className, ...rest }: MoneyInputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="text-card-title text-muted">
        {label}
      </label>
      <input
        {...rest}
        id={inputId}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        value={formatAmount(value)}
        onChange={(event) => onChange(centsFromDigits(event.target.value))}
        className={`border-border bg-surface-raised text-foreground focus:border-accent rounded-lg border px-3 py-2 text-right tabular-nums focus:outline-none ${className ?? ''}`}
      />
    </div>
  );
}

function centsFromDigits(text: string): number {
  const digits = text.replace(/\D/g, '').replace(/^0+/, '').slice(0, MAX_DIGITS);
  return digits === '' ? 0 : Number(digits);
}
