import { useId, type ComponentPropsWithoutRef, type SyntheticEvent } from 'react';

import { formatAmount } from '../lib/money';

/** Up to 13 digits (R$ 99.999.999.999,99), far below Number.MAX_SAFE_INTEGER. */
const MAX_DIGITS = 13;

type MoneyInputProps = Omit<
  ComponentPropsWithoutRef<'input'>,
  'value' | 'onChange' | 'type' | 'inputMode'
> & {
  label: string;
  /** Keeps the label for screen readers only, when the row already names the value. */
  labelHidden?: boolean;
  /** Amount in integer cents. */
  value: number;
  onChange: (cents: number) => void;
};

/**
 * Cash-register style input: every typed digit enters from the right, so "3", "5", "0", "0",
 * "0", "0" shows 3.500,00 and reports 350000 cents. The caret always stays at the end, so a tap
 * in the middle of the number cannot put digits in the wrong place. The value never goes
 * through floating point.
 */
export function MoneyInput({
  label,
  labelHidden = false,
  value,
  onChange,
  id,
  className,
  ...rest
}: MoneyInputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className={labelHidden ? 'sr-only' : 'text-card-title text-muted'}>
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
        onFocus={(event) => {
          keepCaretAtEnd(event);
          rest.onFocus?.(event);
        }}
        onSelect={keepCaretAtEnd}
        className={`border-border bg-surface-raised text-foreground focus:border-accent rounded-lg border px-3 py-2 text-right tabular-nums focus:outline-none ${className ?? ''}`}
      />
    </div>
  );
}

function keepCaretAtEnd(event: SyntheticEvent<HTMLInputElement>): void {
  const input = event.currentTarget;
  const end = input.value.length;
  if (input.selectionStart !== end || input.selectionEnd !== end) {
    input.setSelectionRange(end, end);
  }
}

function centsFromDigits(text: string): number {
  const digits = text.replace(/\D/g, '').replace(/^0+/, '').slice(0, MAX_DIGITS);
  return digits === '' ? 0 : Number(digits);
}
